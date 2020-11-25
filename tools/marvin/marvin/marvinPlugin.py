# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
from sys import stdout, exit
import time
import os
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.marvinInit import MarvinInit
from nose2 import events, runner
from marvin.codes import (SUCCESS,
                          FAILED,
                          EXCEPTION)
from marvin.lib.utils import random_gen
from marvin.cloudstackException import GetDetailExceptionInfo


class MarvinPlugin(events.Plugin):

    """
    Custom plugin for the cloudstackTestCases to be run using nose
    """

    enableOpt = 'with-marvin'
    deployOpt = 'deploy'
    zoneOpt = 'zone'
    hypervisorOpt = 'hypervisor'
    configOpt = 'marvin_config'
    logFolderOpt = 'log_folder_path'
    commandLineSwitch = (None, enableOpt, 'marvin test plugin')
    configSection = 'marvin'
    name = "marvin"

    def __init__(self):
        self.options()
        self.__identifier = None
        self.__testClient = None
        self.__logFolderPath = None
        self.__parsedConfig = None
        '''
        Contains Config File
        '''
        self.__configFile = None
        '''
        Signifies the Zone against which all tests will be Run
        '''
        self.__zoneForTests = None
        '''
        Signifies the flag whether to deploy the New DC or Not
        '''
        self.__deployDcFlag = False
        self.conf = None
        self.__resultStream = stdout
        self.__testRunner = None
        self.__testResult = SUCCESS
        self.__startTime = None
        self.__testName = None
        self.__tcRunLogger = None
        self.__testModName = ''
        self.__hypervisorType = None
        '''
        The Log Path provided by user where all logs are routed to
        '''
        self.__userLogPath = None
        Plugin.__init__(self)

    def handleArgs(self, event):
        """Get our options in order command line, config file, hard coded.
        enable the marvin plugin when the --with-marvin directive is given
        to nose. The enableOpt value is set from the command line directive and
        self.enabled (True|False) determines whether marvin's tests will run.
        By default non-default plugins like marvin will be disabled
        """
        self.enabled = True
        if hasattr(event.args, self.enableOpt):
            if not getattr(event.args, self.enableOpt):
                self.enabled = False
                return
        if hasattr(event.args, self.configOpt):
            if getattr(event.args, self.configOpt):
                self.__configFile = event.args.marvin_config[0]
        if hasattr(event.args, self.deployOpt):
            if getattr(event.args, self.deployOpt):
                self.__deployDcFlag = True
        if hasattr(event.args, self.zoneOpt):
            if getattr(event.args, self.zoneOpt):
                self.__zoneForTests = event.args.zone
        if hasattr(event.args, self.hypervisorOpt):
            if getattr(event.args, self.hypervisorOpt):
                self.setHypervisor(event.args.hypervisor)
        if hasattr(event.args, self.logFolderOpt):
            if getattr(event.args, self.logFolderOpt):
                self.setHypervisor(event.args.logFolder)
        # TODO handle config file config   linkAccount     self.conf = conf
        if self.__configFile:
            if self.startMarvin() == FAILED:
                print("\nStarting Marvin Failed, exiting. Please Check")
                exit(1)
        else:
            print("\nNo marvin config yet, postponing start")

    def options(self):
        """
        Register command line options
        """
        """
        TODO missing is the default=env.get('MARVIN_CONFIG',
                                            './datacenter.cfg'),
        """
        self.addArgument(self.setConfigFile, short_opt="M", long_opt=self.configOpt.replace("_","-"),
                    help_text="Marvin's configuration file is required."
                              "The config file containing the datacenter and "
                              "other management server information is specified")
        self.addFlag(self.enableDeploy, short_opt="R", long_opt=self.deployOpt,
                help_text="Deploys the DC (Region) with Given Configuration."
                          "Required only when DC needs to be deployed")
        self.addArgument(self.setZone, short_opt="Z", long_opt=self.zoneOpt,
                    help_text="Runs all tests against this specified zone")
        self.addArgument(self.setHypervisor, short_opt="H", long_opt=self.hypervisorOpt,
                    help_text="Runs all tests against the specified  zone and hypervisor Type")
        self.addArgument(self.setLogFolder, short_opt="L", long_opt=self.logFolderOpt.replace("_","-"),
                    help_text="Collects all logs under the user specified folder")

    def setConfigFile(self, filePath):
        if filePath != None:
            self.__configFile = filePath

    def setZone(self, zone):
        if zone != None:
            self.__zoneForTests = zone

    def setHypervisor(self, hypervisor):
        if hypervisor != None:
            self.__hypervisorType = hypervisor

    def setLogFolder(self,folder):
        if folder != None:
            self.__logFolderPath = folder

    def enableDeploy(self):
        self.__deployDcFlag = True

    def wantClass(self, cls):
        if cls.__name__ == 'cloudstackTestCase':
            return False
        if issubclass(cls, cloudstackTestCase):
            return True
        return None

    def __checkImport(self, filename):
        '''
        @Name : __checkImport
        @Desc : Verifies to run the available test module for any Import
                Errors before running and check
                whether if it is importable.
                This will check for test modules which has some issues to be
                getting imported.
                Returns False or True based upon the result.
        '''
        try:
            if os.path.isfile(filename):
                ret = os.path.splitext(filename)
                if ret[1] == ".py":
                    os.system("python " + filename)
                    return True
            return False
        except ImportError as e:
            print(("FileName :%s : Error : %s" % \
                  (filename, GetDetailExceptionInfo(e))))
            return False

    def wantFile(self, filename):
        '''
        @Desc : Only python files will be used as test modules
        '''
        return self.__checkImport(filename)

    def loadTestsFromTestCase(self, cls):
        if cls.testCase and cls.testCase.__name__ != 'cloudstackTestCase':
            self.__identifier = cls.testCase.__name__
            self._injectClients(cls.testCase)

    def beforeTest(self, test):
        self.__testModName = test.__str__()
        self.__testName = test.__str__().split()[0]
        if not self.__testName:
            self.__testName = "test"
        self.__testClient.identifier = '-'.\
            join([self.__identifier, self.__testName])
        if self.__tcRunLogger:
            self.__tcRunLogger.name = test.__str__()

    def startTest(self, test):
        """
        Currently used to record start time for tests
        Dump Start Msg of TestCase to Log
        """
        if self.__tcRunLogger:
            self.__tcRunLogger.debug("::::::::::::STARTED : TC: " +
                                     str(self.__testName) + " :::::::::::")
        self.__startTime = time.time()

    def printMsg(self, status, tname, err):
        if status in [FAILED, EXCEPTION] and self.__tcRunLogger:
            self.__tcRunLogger.\
                fatal("%s: %s: %s" % (status,
                                      tname,
                                      GetDetailExceptionInfo(err)))
        write_str = "=== TestName: %s | Status : %s ===\n" % (tname, status)
        self.__resultStream.write(write_str)
        print(write_str)

    def addSuccess(self, test, capt):
        '''
        Adds the Success Messages to logs
        '''
        self.printMsg(SUCCESS, self.__testName, "Test Case Passed")
        self.__testresult = SUCCESS

    def handleError(self, test, err):
        '''
        Adds Exception throwing test cases and information to log.
        '''
        self.printMsg(EXCEPTION, self.__testName, GetDetailExceptionInfo(err))
        self.__testResult = EXCEPTION

    def prepareTestRunner(self, runner):
        if self.__testRunner:
            return self.__testRunner

    def handleFailure(self, test, err):
        '''
        Adds Failing test cases and information to log.
        '''
        self.printMsg(FAILED, self.__testName, GetDetailExceptionInfo(err))
        self.__testResult = FAILED

    def startMarvin(self):
        '''
        @Name : startMarvin
        @Desc : Initializes the Marvin
                creates the test Client
                creates the runlogger for logging
                Parses the config and creates a parsedconfig
                Creates a debugstream for tc debug log
        '''
        try:
            obj_marvininit = MarvinInit(self.__configFile,
                                        self.__deployDcFlag,
                                        None,
                                        self.__zoneForTests,
                                        self.__hypervisorType,
                                        self.__userLogPath)
            if obj_marvininit and obj_marvininit.init() == SUCCESS:
                self.__testClient = obj_marvininit.getTestClient()
                self.__tcRunLogger = obj_marvininit.getLogger()
                self.__parsedConfig = obj_marvininit.getParsedConfig()
                self.__resultStream = obj_marvininit.getResultFile()
                self.__logFolderPath = obj_marvininit.getLogFolderPath()
                # self.__testRunner = runner.PluggableTestRunner(stream=self.__resultStream,
                #                    descriptions=True,
                #                    verbosity=2,
                #                    config=self.conf)
                return SUCCESS
            return FAILED
        except Exception as e:
            print(("Exception Occurred under startMarvin: %s" % \
                  GetDetailExceptionInfo(e)))
            return FAILED

    def stopTest(self, test):
        """
        Currently used to record end time for tests
        """
        endTime = time.time()
        if self.__startTime:
            totTime = int(endTime - self.__startTime)
            if self.__tcRunLogger:
                self.__tcRunLogger.\
                    debug("TestCaseName: %s; "
                          "Time Taken: %s Seconds; StartTime: %s; "
                          "EndTime: %s; Result: %s" %
                          (self.__testName, str(totTime),
                           str(time.ctime(self.__startTime)),
                           str(time.ctime(endTime)),
                           self.__testResult))

    def _injectClients(self, test):
        setattr(test, "debug", self.__tcRunLogger.debug)
        setattr(test, "info", self.__tcRunLogger.info)
        setattr(test, "warn", self.__tcRunLogger.warning)
        setattr(test, "error", self.__tcRunLogger.error)
        setattr(test, "testClient", self.__testClient)
        setattr(test, "config", self.__parsedConfig)
        if self.__testClient.identifier is None:
            self.__testClient.identifier = self.__identifier
        setattr(test, "clstestclient", self.__testClient)
        if hasattr(test, "user"):
            # when the class-level attr applied. all test runs as 'user'
            self.__testClient.getUserApiClient(test.UserName,
                                               test.DomainName,
                                               test.AcctType)

    def finalize(self, result):
        try:
            src = self.__logFolderPath
            tmp = ''
            if not self.__userLogPath:
                log_cfg = self.__parsedConfig.logger
                tmp = log_cfg.__dict__.get('LogFolderPath') + "/MarvinLogs"
            else:
                tmp = self.__userLogPath + "/MarvinLogs"
            dst = tmp + "//" + random_gen()
            mod_name = "test_suite"
            if self.__testModName:
                mod_name = self.__testModName.split(".")
                if len(mod_name) > 2:
                    mod_name = mod_name[-2]
            if mod_name and type(mod_name) is str:
                dst = tmp + "/" + mod_name + "_" + random_gen()
            cmd = "mv " + src + " " + dst
            os.system(cmd)
            print(("===final results are now copied to: %s===" % str(dst)))
        except Exception as e:
            print(("=== Exception occurred under finalize :%s ===" % \
                  str(GetDetailExceptionInfo(e))))
