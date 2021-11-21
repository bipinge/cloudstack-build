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

(
echo o      # Create a new empty DOS partition table
echo n      # Add a new partition
echo p      # Primary partition
echo 1      # Partition number
echo        # First sector
echo 514047 # 250M size
echo n      # Add a new partition
echo p      # Primary partition
echo 2      # Partition number
echo
echo 1026047
echo n      # Add a new partition
echo p      # Primary partition
echo 3      # Partition number
echo
echo
echo a      # Enable Boot flag
echo 1      # Select Partition 1
echo t      # Set partition type
echo 2      # Select Parition 2
echo 82     # Select swap
echo w      # Write changes
) | sudo fdisk $1
sudo fdisk -l $1
