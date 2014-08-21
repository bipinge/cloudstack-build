package com.cloud.hypervisor.ovm3.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XmlTestResultTest {
    private static final String NULL = "<nil/>";
    private String brack(String type, String s) {
        return "<" + type + ">" + s + "</" + type + ">";
    }
    public String errorResponseWrap(String message) {
        return errorResponseWrap(1, message);
    }
    /* example exceptions.OSError:[Errno.17].File.exists: 
     * '/OVS/Repositories/f12842ebf5ed3fe78da1eb0e17f5ede8/VilualDisks/test.raw'
     */
    public String errorResponseWrap(Integer faultCode, String message) {
        String rs = "<?xml version='1.0'?>"
                + "<methodResponse>"
                + "<fault>"
                + "<value><struct>"
                + "<member>"
                + "<name>faultCode</name>"
                + "<value><int>" + faultCode +"</int></value>"
                + "</member>"
                + "<member>"
                + "<name>faultString</name>"
                + "<value><string>" + message + "</string></value>"
                + "</member>"
                + "</struct></value>"
                + "</fault>"
                + "</methodResponse>";
        return rs;
    }

    public String methodResponseWrap(String towrap) {
        return "<?xml version='1.0'?>\n"
                + "<methodResponse>\n"
                + "<params>\n"
                + towrap
                + "</params>\n"
                + "</methodResponse>";
    }
    public String simpleResponseWrapWrapper(String s) {
        return methodResponseWrap("<param>\n"
                + "<value>"
                + s 
                + "</value>\n"
                + "</param>\n");
    }
    /* brack the entire wrap ? :) */
    public String simpleResponseWrap(String type, String s) {
        if (type.contentEquals(NULL)) {
            s = NULL;
        } else {
            s = brack(type, s);
        }
        return simpleResponseWrapWrapper(s);
    }
    public String simpleResponseWrap(String s) {
        return simpleResponseWrapWrapper(s);
    }

    public String getBoolean(boolean bool) {
        String b = "1";
        if (!bool) {
            b = "0";
        }
        return simpleResponseWrap("boolean", b);
    }
    public String getString(String s) {
        return simpleResponseWrap("string", s);
    }
    public String getNil() {
        return simpleResponseWrap(NULL, NULL);
    }

    public void basicBooleanTest(boolean result) {
        basicBooleanTest(result, true);
    }
    public void basicBooleanTest(boolean result, boolean desired) {
        assertNotNull(result);
        assertEquals(desired, result);
    }
    public void basicStringTest(String result, String desired) {
        assertNotNull(result);
        assertEquals(desired, result);
    }
    public void basicIntTest(Integer result, Integer desired) {
        assertNotNull(result);
        assertEquals(desired, result);
    }
}
