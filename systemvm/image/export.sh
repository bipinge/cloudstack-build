#!/bin/bash
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
# Requirements: apt-get install qemu-utils
# Usage: sudo bash -x <this script> <qcow2 systemvm image>
set -x

IMAGE=$1

# Export for XenServer/XCP-ng
qemu-img convert -f qcow2 -O vpc $IMAGE systemvmtemplate-xen.vhd
#bzip2 systemvmtemplate-xen.vhd

# Export for VMware
qemu-img convert -f qcow2 -O vmdk $IMAGE systemvmtemplate-vmware-disk1.vmdk
size=$(stat --printf="%s" systemvmtemplate-vmware-disk1.vmdk)
cat <<EOF > systemvmtemplate-vmware.ovf
<?xml version="1.0" encoding="UTF-8"?>
<Envelope vmw:buildId="build-2459827" xmlns="http://schemas.dmtf.org/ovf/envelope/1" xmlns:cim="http://schemas.dmtf.org/wbem/wscim/1/common" xmlns:ovf="http://schemas.dmtf.org/ovf/envelope/1" xmlns:rasd="http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_ResourceAllocationSettingData" xmlns:vmw="http://www.vmware.com/schema/ovf" xmlns:vssd="http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_VirtualSystemSettingData" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <References>
    <File ovf:href="systemvmtemplate-vmware-disk1.vmdk" ovf:id="file1" ovf:size="$size"/>
  </References>
  <DiskSection>
    <Info>Virtual disk information</Info>
    <Disk ovf:capacity="5" ovf:capacityAllocationUnits="byte * 2^30" ovf:diskId="vmdisk1" ovf:fileRef="file1" ovf:format="http://www.vmware.com/interfaces/specifications/vmdk.html#streamOptimized" ovf:populatedSize="$size"/>
  </DiskSection>
  <VirtualSystem ovf:id="vm">
    <Info>A virtual machine</Info>
    <Name>systemvmtemplate-vmware</Name>
    <OperatingSystemSection ovf:id="101" vmw:osType="otherLinux64Guest">
      <Info>The kind of installed guest operating system</Info>
    </OperatingSystemSection>
    <VirtualHardwareSection>
      <Info>Virtual hardware requirements</Info>
      <System>
        <vssd:ElementName>Virtual Hardware Family</vssd:ElementName>
        <vssd:InstanceID>0</vssd:InstanceID>
        <vssd:VirtualSystemIdentifier>systemvmtemplate-vmware</vssd:VirtualSystemIdentifier>
        <vssd:VirtualSystemType>vmx-11</vssd:VirtualSystemType>
      </System>
      <Item>
        <rasd:AllocationUnits>hertz * 10^6</rasd:AllocationUnits>
        <rasd:Description>Number of Virtual CPUs</rasd:Description>
        <rasd:ElementName>1 virtual CPU(s)</rasd:ElementName>
        <rasd:InstanceID>1</rasd:InstanceID>
        <rasd:ResourceType>3</rasd:ResourceType>
        <rasd:VirtualQuantity>1</rasd:VirtualQuantity>
      </Item>
      <Item>
        <rasd:AllocationUnits>byte * 2^20</rasd:AllocationUnits>
        <rasd:Description>Memory Size</rasd:Description>
        <rasd:ElementName>256MB of memory</rasd:ElementName>
        <rasd:InstanceID>2</rasd:InstanceID>
        <rasd:ResourceType>4</rasd:ResourceType>
        <rasd:VirtualQuantity>256</rasd:VirtualQuantity>
      </Item>
      <Item>
        <rasd:Address>0</rasd:Address>
        <rasd:Description>SCSI Controller</rasd:Description>
        <rasd:ElementName>scsiController0</rasd:ElementName>
        <rasd:InstanceID>3</rasd:InstanceID>
        <rasd:ResourceSubType>lsilogic</rasd:ResourceSubType>
        <rasd:ResourceType>6</rasd:ResourceType>
      </Item>
      <Item>
        <rasd:Address>0</rasd:Address>
        <rasd:Description>IDE Controller</rasd:Description>
        <rasd:ElementName>ideController0</rasd:ElementName>
        <rasd:InstanceID>4</rasd:InstanceID>
        <rasd:ResourceType>5</rasd:ResourceType>
      </Item>
      <Item ovf:required="false">
        <rasd:AddressOnParent>0</rasd:AddressOnParent>
        <rasd:AutomaticAllocation>false</rasd:AutomaticAllocation>
        <rasd:ElementName>cdrom0</rasd:ElementName>
        <rasd:InstanceID>5</rasd:InstanceID>
        <rasd:Parent>4</rasd:Parent>
        <rasd:ResourceType>15</rasd:ResourceType>
      </Item>
      <Item>
        <rasd:AddressOnParent>0</rasd:AddressOnParent>
        <rasd:ElementName>disk0</rasd:ElementName>
        <rasd:HostResource>ovf:/disk/vmdisk1</rasd:HostResource>
        <rasd:InstanceID>6</rasd:InstanceID>
        <rasd:Parent>3</rasd:Parent>
        <rasd:ResourceType>17</rasd:ResourceType>
      </Item>
      <Item ovf:required="false">
        <rasd:AutomaticAllocation>false</rasd:AutomaticAllocation>
        <rasd:ElementName>video</rasd:ElementName>
        <rasd:InstanceID>7</rasd:InstanceID>
        <rasd:ResourceType>24</rasd:ResourceType>
        <vmw:Config ovf:required="false" vmw:key="enable3DSupport" vmw:value="false"/>
        <vmw:Config ovf:required="false" vmw:key="useAutoDetect" vmw:value="false"/>
        <vmw:Config ovf:required="false" vmw:key="videoRamSizeInKB" vmw:value="4096"/>
      </Item>
      <Item ovf:required="false">
        <rasd:AutomaticAllocation>false</rasd:AutomaticAllocation>
        <rasd:ElementName>vmci</rasd:ElementName>
        <rasd:InstanceID>8</rasd:InstanceID>
        <rasd:ResourceSubType>vmware.vmci</rasd:ResourceSubType>
        <rasd:ResourceType>1</rasd:ResourceType>
      </Item>
      <vmw:Config ovf:required="false" vmw:key="cpuHotAddEnabled" vmw:value="false"/>
      <vmw:Config ovf:required="false" vmw:key="cpuHotRemoveEnabled" vmw:value="false"/>
      <vmw:Config ovf:required="false" vmw:key="firmware" vmw:value="bios"/>
      <vmw:Config ovf:required="false" vmw:key="memoryHotAddEnabled" vmw:value="false"/>
    </VirtualHardwareSection>
    <AnnotationSection ovf:required="false">
      <Info>A human-readable annotation</Info>
      <Annotation>systemvmtemplate-vmware</Annotation>
    </AnnotationSection>
  </VirtualSystem>
</Envelope>
EOF

cat <<EOF > systemvmtemplate-vmware.mf
SHA1(systemvmtemplate-vmware.ovf)= $(sha1sum systemvmtemplate-vmware.ovf|awk '{print $1}')
SHA1(systemvmtemplate-vmware-disk1.vmdk)= $(sha1sum systemvmtemplate-vmware-disk1.vmdk |awk '{print $1}')
EOF

tar -cvf systemvmtemplate-vmware.ova --format=ustar systemvmtemplate-vmware.ovf systemvmtemplate-vmware.mf systemvmtemplate-vmware-disk1.vmdk
rm -f systemvmtemplate-vmware.ovf systemvmtemplate-vmware.mf systemvmtemplate-vmware-disk1.vmdk

# Create checksums
md5sum systemvmtemplate* > md5sum.txt
sha512sum systemvmtemplate* > sha512sum.txt
