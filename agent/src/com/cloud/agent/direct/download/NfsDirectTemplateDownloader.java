//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
package com.cloud.agent.direct.download;

import com.cloud.utils.UriUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public class NfsDirectTemplateDownloader extends DirectTemplateDownloaderImpl {

    private String srcHost;
    private String srcPath;

    private static final String mountCommand = "mount -t nfs %s %s";

    /**
     * Parse url and set srcHost and srcPath
     */
    private void parseUrl() {
        URI uri = null;
        String url = getUrl();
        try {
            uri = new URI(UriUtils.encodeURIComponent(url));
            if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("nfs")) {
                srcHost = uri.getHost();
                srcPath = uri.getPath();
            }
        } catch (URISyntaxException e) {
            throw new CloudRuntimeException("Invalid NFS url " + url + " caused error: " + e.getMessage());
        }
    }

    public NfsDirectTemplateDownloader(String url, String destPool, Long templateId, String checksum) {
        super(url, destPool, templateId, checksum);
        parseUrl();
    }

    @Override
    public boolean downloadTemplate() {
        String mountSrcUuid = UUID.randomUUID().toString();
        String mount = String.format(mountCommand, srcHost + ":" + srcPath, "/mnt/" + mountSrcUuid);
        Script.runSimpleBashScript(mount);
        String downloadDir = getDestPoolPath() + File.separator + getDirectDownloadTempPath(getTemplateId());
        setDownloadedFilePath(downloadDir + File.separator + getFileNameFromUrl());
        Script.runSimpleBashScript("cp /mnt/" + mountSrcUuid + srcPath + " " + getDownloadedFilePath());
        Script.runSimpleBashScript("umount /mnt/" + mountSrcUuid);
        return true;
    }
}
