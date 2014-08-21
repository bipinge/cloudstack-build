/*******************************************************************************
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
/* contains VM related stuff too */

package com.cloud.hypervisor.ovm3.object;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class Xen extends OvmObject {
    private static final Logger LOGGER = Logger
            .getLogger(Xen.class);
    private Map<String, Vm> vmList = null;
    /* TODO: for now, but just insert a "default" VM in the Xen class */
    private Vm defVm = new Vm();

    public Xen(Connection c) {
        setClient(c);
    }

    /* a vm class.... Setting up a VM is different than retrieving one from OVM.
     * It's either a list retrieval or /usr/lib64/python2.4/site-packages/agent/lib/xenvm.py */
    public static class Vm {
        /* 'vfb': [ 'type=vnc,vncunused=1,vnclisten=127.0.0.1,keymap=en-us'] */
        private List<String> vmVncElement = new ArrayList<String>();
        private Map<String, String> vmVnc = new HashMap<String, String>() {
            {
                put("type", "vnc");
                put("vncunused", "1");
                put("vnclisten", "127.0.0.1");
                put("keymap", "en-us");
            }
        };

        /*'disk': [
         * 'file:/OVS/Repositories/0004fb0000030000aeaca859e4a8f8c0/VirtualDisks/0004fb0000120000c444117fd87ea251.img,xvda,w']
         */
        private List<String> vmDisks = new ArrayList<String>();
        private Map<String, String> vmDisk = new HashMap<String, String>() {
            {
                put("id", "");
                put("uuid", "");
                put("dev", "");
                put("bootable", "1");
                put("mode", "w");
                put("VDI", "");
                put("backend", "0");
                put("protocol", "x86_32-abi");
                put("uname", "");
            }
        };

        /* 'vif': [ 'mac=00:21:f6:00:00:00,bridge=c0a80100'] */
        private String[] vmVifs = new String[6];
        private  List<String> vmVifsPrep = new ArrayList<String>();
        private String vmSimpleName = "";
        private String vmName = "";
        private String vmUuid = "";
        /*
         * the pool the vm.cfg will live on, this is the same as the primary
         * storage pool (should be unified with disk pool ?)
         */
        private String vmPrimaryPoolUuid = "";
        private String vmOnReboot = "restart";
        /* weight is relative for all VMs compared to each other */
        private int vmCpuWeight = 27500;
        /* minimum memory allowed */
        private int vmMemory = 256;
        private int vmCpuCap = 0;
        /* dynam scaling for cpus */
        private int vmMaxVcpus = 0;
        /* default to 1, can't be higher than maxvCpus */
        private int vmVcpus = 1;
        /* high available */
        private Boolean vmHa = false;
        private String vmDescription = "";
        private String vmOnPoweroff = "destroy";
        private String vmOnCrash = "restart";
        private String vmBootloader = "/usr/bin/pygrub";
        private String vmBootArgs = "";
        private String vmExtra = "";
        /* default to linux */
        private String vmOs = "Other Linux";
        private String vmCpuCompatGroup = "";
        /* pv is default */
        private String vmDomainType = "xen_pvm";
        /* TODO: check of we really vmState, defaults to "------" */
        /* start counting disks at A -> 0 */
        private int diskZero = 97;
        private int diskCount = diskZero;

        /* TODO: Subclass vm parameters */
        private Map<String, Object> vmParams = new HashMap<String, Object>() {
            {
                put("vif", vmVifsPrep);
                put("OVM_simple_name", vmSimpleName);
                put("disk", vmDisks);
                put("bootargs", vmBootArgs);
                put("uuid", vmUuid);
                put("on_reboot", vmOnReboot);
                put("cpu_weight", vmCpuWeight);
                put("memory", vmMemory);
                put("cpu_cap", vmCpuCap);
                put("maxvcpus", vmMaxVcpus);
                put("OVM_high_availability", vmHa);
                put("OVM_description", vmDescription);
                put("on_poweroff", vmOnPoweroff);
                put("on_crash", vmOnCrash);
                put("bootloader", vmBootloader);
                put("name", vmName);
                put("guest_os_type", vmOs);
                put("vfb", vmVncElement);
                put("vcpus", vmVcpus);
                put("OVM_cpu_compat_group", vmCpuCompatGroup);
                put("OVM_domain_type", vmDomainType);
                put("extra", vmExtra);
            }
        };

        public boolean isControlDomain() {
            if (this.getVmUuid().contains(
                    "00000000-0000-0000-0000-000000000000") ||
                    this.getVmName().contains("Domain-0")) {
                return true;
            }
            return false;
        }

        public boolean setPrimaryPoolUuid(String poolId) {
            this.vmPrimaryPoolUuid = poolId;
            return true;
        }

        public String getPrimaryPoolUuid() throws Ovm3ResourceException {
            if (this.vmPrimaryPoolUuid.equals("")) {
                return this.getVmRootDiskPoolId();
            } else {
                return this.vmPrimaryPoolUuid;
            }
        }

        public Map<String, Object> getVmParams() {
            return this.vmParams;
        }

        public void setVmParams(Map<String, Object> params) {
            this.vmParams = params;
        }

        public boolean setVmExtra(final String args) {
            vmParams.put("extra", args);
            return true;
        }

        public String getVmExtra() {
            return (String) vmParams.get("extra");
        }

        public boolean setVmBootArgs(final String args) {
            vmParams.put("bootargs", args);
            return true;
        }

        public String getVmBootArgs() {
            return (String) vmParams.get("bootloader_args");
        }

        public Boolean setVmMaxCpus(Integer val) {
            if (getVmCpus() > val) {
                vmParams.put("maxvcpus", getVmCpus());
            } else {
                vmParams.put("maxvcpus", val);
            }
            return true;
        }

        public Integer getVmMaxCpus() {
            return (Integer) vmParams.get("maxvcpus");
        }

        public Boolean setVmCpus(Integer val) {
            vmParams.put("vcpus", val);
            if (getVmMaxCpus() < val) {
                setVmMaxCpus(val);
            }
            return true;
        }

        public Integer getVmCpus() {
            return (Integer) vmParams.get("vcpus");
        }

        public Boolean setVmMemory(long memory) {
            vmParams.put("memory", Long.toString(memory));
            return true;
        }

        public long getVmMemory() {
            return Long.parseLong((String) vmParams.get("memory"));
        }

        public Boolean setVmDomainType(String domtype) {
            vmParams.put("OVM_domain_type", domtype);
            return true;
        }

        /* iiiis this a good idea ? */
        public String getVmDomainType() {
            String domType = (String) vmParams.get("OVM_domain_type");
            if (domType == null) {
                String builder = (String) vmParams.get("builder");
                if (builder.contains("linux")) {
                    domType = "xen_pvm";
                } else {
                    domType = "hvm";
                }
            }
            return domType;
        }

        public Boolean setVmState(String state) {
            vmParams.put("state", state);
            return true;
        }

        public String getVmState() {
            return (String) vmParams.get("state");
        }

        public Boolean setVmName(String name) {
            vmParams.put("name", name);
            vmParams.put("OVM_simple_name", name);
            return true;
        }

        public String getVmName() {
            return (String) vmParams.get("name");
        }

        public Boolean setVmUuid(String uuid) {
            vmParams.put("uuid", uuid);
            return true;
        }

        public String getVmUuid() {
            return (String) vmParams.get("uuid");
        }

        /* TODO: splork out VIFs this is not sane, same for VFBs and */
        public void setVmVncs(List<String> vncs) {
            this.vmVncElement.addAll(vncs);
        }

        public List<String> getVmVncs() {
            return this.vmVncElement;
        }

        public void setVmDisks(List<String> disks) {
            this.vmDisks.addAll(disks);
        }

        public List<String> getVmDisks() {
            return this.vmDisks;
        }

        public void setVmVifs(List<String> vifs) {
            this.vmVifsPrep.addAll(vifs);
        }

        public List<String> getVmVifs() {
            return this.vmVifsPrep;
        }

        public Boolean addVif(Integer id, String bridge, String mac) {
            String vif = "mac=" + mac + ",bridge=" + bridge;
            vmVifs[id] = vif;
            vmVifsPrep.add(vif);
            return true;
        }

        public Boolean removeVif(String bridge, String mac) {
            String vifLine = "mac="+mac+",bridge="+bridge;
            for (String vif : vmVifsPrep) {
                if (vifLine.equals(vif)) {
                    return vmVifsPrep.remove(vif);
                }
            }
            LOGGER.debug("No vif found corresponding to mac: " + mac + ", bridge " + bridge);
            return false;
        }

        /* 'file:/OVS/Repositories/d5f5a4480515467ca1638554f085b278/ISOs/e14c811ebbf84f0b8221e5b7404a554e.iso,hdc:cdrom,r' */
        /* device is coupled with vmtype enumerate and cdboot ? */
        public Boolean addRootDisk(String image) {
            Boolean ret = false;
            if (diskCount > diskZero) {
                Integer oVmDisk = diskCount;
                diskCount = diskZero;
                ret = addDisk(image, "w");
                diskCount = oVmDisk;
            } else {
                ret = addDisk(image, "w");
            }
            return ret;
        }

        public Boolean addDataDisk(String image) {
            /* w! means we're able to share the disk is that wise, should be an
             * option in CS ?
             */
            return addDisk(image, "w!");
        }

        public Boolean addIso(String image) {
            /* should we check for .iso ? */
            return addDisk(image, "r!");
        }

        public Boolean addDisk(String image, String mode) {
            String devName = null;
            /* better accounting then diskCount += 1 */
            diskCount = diskZero + vmDisks.size();
            if (getVmDomainType() != null && getVmDomainType().contains("hvm")) {
                diskCount += 2;
                devName = Character.toString((char) diskCount);
            } else {
                devName = "xvd" + Character.toString((char) diskCount);
            }

            /* check for iso, force mode and additions */
            if (image.endsWith(".iso")) {
                devName = devName + ":cdrom";
                mode = "r";
            }
            return addDiskToDisks(image, devName, mode);
        }

        /* should be on device id too, or else we get random attaches... */
        private Boolean addDiskToDisks(String image, String devName, String mode) {
            /* TODO: needs to become "checkDisk" */
            for (String disk : vmDisks) {
                if (disk.contains(image)) {
                    return true;
                }
            }
            vmDisks.add("file:" + image + "," + devName + "," + mode);
            vmParams.put("disk", vmDisks);
            return true;
        }

        public Boolean removeDisk(String image) {
            for (String disk : vmDisks) {
                if (disk.contains(image)) {
                    return vmDisks.remove(disk);
                }
            }
            LOGGER.debug("No disk found corresponding to image: " +image);
            return false;
        }

        /* The conflict between getVm and getVmConfig becomes clear */
        public String getVmRootDiskPoolId() throws Ovm3ResourceException {
            String poolId = getVmDiskPoolId(0);
            this.setPrimaryPoolUuid(poolId);
            return poolId;
        }

        /* TODO: need to fork out vifs, disks and vnc stuff fill them nicely too */
        public String getVmDiskPoolId(int disk) throws Ovm3ResourceException {
            int fi = 3;
            String diskPath = "";
            try {
                diskPath = getVmDiskDetailFromMap(disk, "uname");
            } catch (NullPointerException e) {
                throw new Ovm3ResourceException("No valid disk found for id: " + disk);
            }
            String[] st = diskPath.split(File.separator);
            return st[fi];
        }

        private String getVmDiskDetailFromMap(int disk, String dest) {
            Map<String, Object[]> o = (Map<String, Object[]>) vmParams
                    .get("device");
            vmDisk = (Map<String, String>) o.get("vbd")[disk];
            return vmDisk.get(dest);
        }

        public Boolean removeDisk(String file, String device) {
            /* get index and remove */
            return true;
        }

        public boolean setVnc() {
            List<String> vfb = new ArrayList<String>();
            for (final String key : vmVnc.keySet()) {
                vfb.add(key + "=" + vmVnc.get(key));
            }
            vmVncElement.add(StringUtils.join(vfb, ","));
            return true;
        }

        public Boolean setVnc(String address, String password) {
            setVncAddress(address);
            setVncPassword(password);
            return setVnc();
        }

        public void setVncUsed(String used) {
            vmVnc.put("vncused", used);
        }

        public String getVncUsed() {
            return vmVnc.get("vncused");
        }

        public void setVncPassword(String pass) {
            vmVnc.put("vncpasswd", pass);
        }

        public String getVncPassword() {
            return vmVnc.get("vncpasswd");
        }

        public void setVncAddress(String address) {
            vmVnc.put("vnclisten", address);
        }

        public String getVncAddress() {
            Integer port = getVncPort();
            if (port == null) {
                return null;
            }
            return vmVnc.get("vnclisten");
        }

        public Integer getVncPort() {
            if (getFromVncMap("port") != null) {
                return Integer.parseInt(getFromVncMap("port"));
            }
            String vnc = getVncLocation();
            if (vnc.contains(":")) {
                final String[] res = vnc.split(":");
                vmVnc.put("vnclisten", res[0]);
                vmVnc.put("port", res[1]);
                return Integer.parseInt(res[1]);
            }
            return null;
        }

        public String getVncLocation() {
            return getFromVncMap("location");
        }

        private String getFromVncMap(String el) {
            Map<String, Object[]> o = (Map<String, Object[]>) vmParams
                    .get("device");
            vmVnc = (Map<String, String>) o.get("vfb")[0];
            if (vmVnc.containsKey(el)) {
                return vmVnc.get(el);
            } else {
                return null;
            }
        }

        /* Don't think we'll use this? */
        public Boolean removeVfb(String type, String address, String map) {
            /* get index and remove */
            return true;
        }

        public Object get(String key) {
            return vmParams.get(key);
        }

        public <T> boolean set(String key, T arg) {
            vmParams.put(key, arg);
            return true;
        }
    }

    /*
     * delete_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: assembly_id -
     * default: None
     */

    /*
     * unconfigure_template, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * template_id - default: None argument: params - default: None
     */

    /*
     * sysrq_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: letter - default: None
     */

    /*
     * list_vms, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None
     */
    public Map<String, Vm> listVms() throws Ovm3ResourceException {
        Object[] result = (Object[]) callWrapper("list_vms");
        if (result == null) {
            return null;
        }

        try {
            vmList = new HashMap<String, Vm>();
            for (Object x : result) {
                /* put the vmparams in, as x is a hashmap */
                Vm vm = new Vm();
                vm.setVmParams((Map<String, Object>) x);
                vmList.put((String) vm.get("name"), vm);
            }
        } catch (Exception e) {
            String msg = "Unable to list VMs: " + e.getMessage();
            throw new Ovm3ResourceException(msg, e);
        }
        return vmList;
    }

    /*
     * this should become getVmConfig later... getVmConfig returns the
     * configuration file, while getVm returns the "live" configuration. It
     * makes perfect sense if you think about it..... ....long enough
     */
    public Vm getRunningVmConfig(String name) throws Ovm3ResourceException {
        return getRunningVmConfigs().get(name);
    }

    public Map<String, Vm> getRunningVmConfigs() throws Ovm3ResourceException {
        return listVms();
    }

    /*
     * delete_vm_core, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: core_date - default: None
     */

    /*
     * delete_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
    public Boolean deleteVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("delete_vm", repoId, vmId);
    }

    /*
     * save_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: checkpoint - default: None
     *//* add checkpoint */
    public Boolean saveVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("save_vm", repoId, vmId);
    }

    /*
     * configure_template, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * template_id - default: None argument: params - default: None
     */

    /*
     * create_template, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: template_id -
     * default: None argument: params - default: None
     */

    /*
     * list_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */

    public Boolean listVm(String repoId, String vmId) throws Ovm3ResourceException {
        defVm.setVmParams((Map<String,Object>) callWrapper("list_vm", repoId, vmId));
        if (defVm.getVmParams() == null) {
            return false;
        }
        return true;
    }

    /*
     * dump_vm_core, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: live - default: None argument: crash - default:
     * None argument: reset - default: None
     */

    /*
     * assembly_del_file, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * assembly_id - default: None argument: filename - default: None
     */

    /*
     * get_template_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * template_id - default: None
     */

    /*
     * set_assembly_config_xml, <class 'agent.api.hypervisor.xenxm.Xen'>
     * argument: self - default: None argument: repo_id - default: None
     * argument: assembly_id - default: None argument: cfg - default: None
     */

    /*
     * assembly_add_file, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * assembly_id - default: None argument: url - default: None argument:
     * filename - default: None argument: option - default: None
     */

    /*
     * send_to_guest, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: params - default: None
     */

    /*
     * set_assembly_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * assembly_id - default: None argument: cfg - default: None
     */

    /*
     * configure_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: params - default: None
     */
    public Boolean configureVm(String repoId, String vmId,
            Map<String, Object> params) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("configure_vm", repoId, vmId, params);
    }

    public Boolean configureVm(String repoId, String vmId) throws Ovm3ResourceException {
        return configureVm(repoId, vmId, this.defVm.getVmParams());
    }

    /*
     * cleanup_migration_target, <class 'agent.api.hypervisor.xenxm.Xen'>
     * argument: self - default: None argument: repo_id - default: None
     * argument: vm_id - default: None
     */

    /*
     * pause_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
    public Boolean pauseVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("pause_vm", repoId, vmId);
    }

    /*
     * setup_migration_target, <class 'agent.api.hypervisor.xenxm.Xen'>
     * argument: self - default: None argument: repo_id - default: None
     * argument: vm_id - default: None
     */

    /*
     * deploy_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: assembly_id -
     * default: None argument: to_deploy - default: None argument:
     * target_repo_id - default: None argument: option - default: None
     */

    /*
     * stop_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: force - default: None
     */
    public Boolean stopVm(String repoId, String vmId) throws Ovm3ResourceException {
        Object x = callWrapper("stop_vm", repoId, vmId, false);
        if (x == null) {
            return true;
        }
        return false;
    }

    public Boolean stopVm(String repoId, String vmId, Boolean force) throws Ovm3ResourceException{
        Object x = callWrapper("stop_vm", repoId, vmId, force);
        if (x == null) {
            return true;
        }
        return false;
    }

    /*
     * set_template_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * template_id - default: None argument: params - default: None
     */

    /*
     * assembly_rename_file, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * assembly_id - default: None argument: filename - default: None argument:
     * new_filename - default: None
     */

    /*
     * migrate_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: dest - default: None argument: live - default:
     * None argument: ssl - default: None
     */
    public Boolean migrateVm(String repoId, String vmId, String dest) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("migrate_vm", repoId, vmId, dest);
    }

    public Boolean migrateVm(String repoId, String vmId, String dest,
            boolean live, boolean ssl) throws Ovm3ResourceException {
        Object x = callWrapper("migrate_vm", repoId, vmId, dest, live, ssl);
        if (x == null) {
            return true;
        }
        return false;
    }

    /*
     * configure_vm_ha, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: enable_ha - default: None
     */
    public Boolean configureVmHa(String repoId, String vmId, Boolean ha) throws Ovm3ResourceException {
        Object x = callWrapper("configure_vm_ha", repoId, vmId, ha);
        if (x == null) {
            return true;
        }
        return false;
    }

    /*
     * create_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: params - default: None
     */
    public Boolean createVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("create_vm", repoId, vmId, defVm.getVmParams());
    }

    public Boolean createVm(String repoId, String vmId,
            Map<String, Object> vmParams) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("create_vm", repoId, vmId, vmParams);
    }

    /*
     * pack_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: assembly_id -
     * default: None
     */

    /*
     * restore_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: paused - default: None
     */

    /*
     * start_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
    public Boolean startVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("start_vm", repoId, vmId);
    }

    /*
     * unpause_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
    public Boolean unpauseVm(String repoId, String vmId) throws Ovm3ResourceException {
        return nullIsTrueCallWrapper("unpause_vm", repoId, vmId);
    }

    /*
     * trigger_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: name - default: None argument: vcpu - default:
     * None
     */

    /*
     * set_vm_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: params - default: None
     */

    /*
     * delete_template, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: template_id -
     * default: None
     */

    /*
     * reboot_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: wait - default: None
     */
    public Boolean rebootVm(String repoId, String vmId, int wait) throws Ovm3ResourceException {
        Object x = callWrapper("reboot_vm", repoId, vmId, wait);
        if (x == null) {
            return true;
        }
        return false;
    }

    public Boolean rebootVm(String repoId, String vmId) throws Ovm3ResourceException {
        Object x = callWrapper("reboot_vm", repoId, vmId, 3);
        if (x == null) {
            return true;
        }
        return false;
        // throw new Ovm3ResourceException(x);
    }

    /*
     * unpack_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: assembly_id -
     * default: None
     */

    /*
     * get_vm_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
    public Vm getVmConfig(String vmName) throws Ovm3ResourceException {
        defVm = this.getRunningVmConfig(vmName);
        if (defVm == null) {
            return defVm;
        }
        return getVmConfig(defVm.getVmRootDiskPoolId(), defVm.getVmUuid());
    }

    public Vm getVmConfig() {
        return defVm;
    }

    /*
     * returns the configuration file contents, so we parse it for configuration
     * alterations we might want to do (/$repo/VirtualMachines/$uuid/vm.cfg)
     */
    public Vm getVmConfig(String repoId, String vmId) throws Ovm3ResourceException {
        try {
            Xen.Vm nVm = new Xen.Vm();
            Map<String, Object[]> x = (Map<String, Object[]>) callWrapper(
                    "get_vm_config", repoId, vmId);
            if (x == null) {
                return nVm;
            }
            nVm.setVmVifs(Arrays.asList(Arrays.copyOf(x.get("vif"),
                    x.get("vif").length, String[].class)));
            x.remove("vif");
            nVm.setVmDisks(Arrays.asList(Arrays.copyOf(x.get("disk"),
                    x.get("disk").length, String[].class)));
            x.remove("disk");
            nVm.setVmVncs(Arrays.asList(Arrays.copyOf(x.get("vfb"),
                    x.get("vfb").length, String[].class)));
            x.remove("vfb");
            Map<String, Object> remains = new HashMap<String, Object>();
            for (final Map.Entry<String, Object[]> not : x.entrySet()) {
                remains.put(not.getKey(), not.getValue());
            }
            nVm.setVmParams(remains);
            nVm.setPrimaryPoolUuid(repoId);
            /* to make sure stuff doesn't blow up in our face... */
            defVm = nVm;
            return nVm;
        } catch (Ovm3ResourceException e) {
            throw e;
        }
    }

    /*
     * get_assembly_config_xml, <class 'agent.api.hypervisor.xenxm.Xen'>
     * argument: self - default: None argument: repo_id - default: None
     * argument: assembly_id - default: None
     */

    /*
     * import_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: assembly_id -
     * default: None argument: url - default: None argument: option - default:
     * None
     */

    /*
     * create_assembly, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: assembly_id -
     * default: None argument: templates - default: None
     */

    /*
     * get_assembly_config, <class 'agent.api.hypervisor.xenxm.Xen'> argument:
     * self - default: None argument: repo_id - default: None argument:
     * assembly_id - default: None
     */

    /*
     * unconfigure_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: params - default: None
     */

    /*
     * import_template, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self
     * - default: None argument: repo_id - default: None argument: template_id -
     * default: None argument: url_list - default: None argument: option -
     * default: None
     */

    /*
     * import_vm, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None argument: url_list - default: None argument: option -
     * default: None
     */

    /*
     * list_vm_core, <class 'agent.api.hypervisor.xenxm.Xen'> argument: self -
     * default: None argument: repo_id - default: None argument: vm_id -
     * default: None
     */
}
