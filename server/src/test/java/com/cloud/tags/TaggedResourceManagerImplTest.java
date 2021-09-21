/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.cloud.tags;

import com.cloud.server.ResourceTag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TaggedResourceManagerImplTest extends TestCase{

    @Spy
    private final TaggedResourceManagerImpl taggedResourceManagerImplSpy = new TaggedResourceManagerImpl();

    private final List<ResourceTag.ResourceObjectType> listResourceObjectTypes = Arrays.asList(ResourceTag.ResourceObjectType.values());

    @Test
    public void validateGetTagsFromResourceMustReturnValues(){
        Map<String, String> expectedResult = new HashMap<>();
        expectedResult.put("test1", "test1");
        expectedResult.put("test2", "test2");

        listResourceObjectTypes.forEach(resourceObjectType -> {
            List<ResourceTag> resourceTags = new ArrayList<>();
            expectedResult.entrySet().forEach(entry -> {
                resourceTags.add(new ResourceTagVO(entry.getKey(), entry.getValue(), 0, 0, 0, resourceObjectType, "test", "test"));
            });

            Mockito.doReturn(resourceTags).when(taggedResourceManagerImplSpy).listByResourceTypeAndId(Mockito.eq(resourceObjectType), Mockito.anyLong());
            Map<String, String> result = taggedResourceManagerImplSpy.getTagsFromResource(resourceObjectType, 0l);
            Assert.assertEquals(expectedResult, result);
        });
    }

    @Test
    public void validateGetTagsFromResourceMustReturnNull(){
        Map<String, String> expectedResult = null;

        listResourceObjectTypes.forEach(resourceObjectType -> {
            List<ResourceTag> resourceTags = null;

            Mockito.doReturn(resourceTags).when(taggedResourceManagerImplSpy).listByResourceTypeAndId(Mockito.eq(resourceObjectType), Mockito.anyLong());
            Map<String, String> result = taggedResourceManagerImplSpy.getTagsFromResource(resourceObjectType, 0l);
            Assert.assertEquals(expectedResult, result);
        });
    }

    @Test
    public void validateGetTagsFromResourceMustReturnEmpty(){
        Map<String, String> expectedResult = new HashMap<>();

        listResourceObjectTypes.forEach(resourceObjectType -> {
            List<ResourceTag> resourceTags = new ArrayList<>();

            Mockito.doReturn(resourceTags).when(taggedResourceManagerImplSpy).listByResourceTypeAndId(Mockito.eq(resourceObjectType), Mockito.anyLong());
            Map<String, String> result = taggedResourceManagerImplSpy.getTagsFromResource(resourceObjectType, 0l);
            Assert.assertEquals(expectedResult, result);
        });
    }
}
