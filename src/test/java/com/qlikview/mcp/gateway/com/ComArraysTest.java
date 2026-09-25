package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.Dispatch;
import com.sun.jna.platform.win32.COM.util.Factory;
import com.sun.jna.platform.win32.COM.util.IRawDispatchHandle;
import com.sun.jna.platform.win32.COM.util.annotation.ComObject;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies SAFEARRAY unpacking against real Windows COM structures built via
 * {@code OleAuto.SafeArrayCreate}/{@code SafeArrayPutElement}. The {@code VT_DISPATCH} case uses
 * {@code Scripting.Dictionary}, a COM automation object present on every Windows install, as the
 * {@code IDispatch} source, exercising the same {@link Factory#createProxy} path used for
 * QlikView's own objects.
 */
@EnabledOnOs(OS.WINDOWS)
class ComArraysTest {

    @ComObject(progId = "Scripting.Dictionary")
    private interface Dictionary extends IRawDispatchHandle {
    }

    @Test
    void toStringArrayReturnsEmptyArrayForNull() {
        assertThat(ComArrays.toStringArray(null)).isEmpty();
    }

    @Test
    void toDispatchArrayReturnsEmptyListForNull() {
        List<Object> result = ComArrays.toDispatchArray(null, Object.class, null);
        assertThat(result).isEmpty();
    }

    @Test
    void toStringArrayUnpacksRealSafeArrayOfStrings() {
        SAFEARRAY.ByReference array = SAFEARRAY.createSafeArray(
            new com.sun.jna.platform.win32.WTypes.VARTYPE(Variant.VT_BSTR), 3);
        try {
            array.putElement("first", 0);
            array.putElement("second", 1);
            array.putElement("third", 2);

            String[] result = ComArrays.toStringArray(array);

            assertThat(result).containsExactly("first", "second", "third");
        } finally {
            array.close();
        }
    }

    @Test
    void toStringArrayReturnsEmptyArrayForZeroLengthSafeArray() {
        SAFEARRAY.ByReference array = SAFEARRAY.createSafeArray(
            new com.sun.jna.platform.win32.WTypes.VARTYPE(Variant.VT_BSTR), 0);
        try {
            String[] result = ComArrays.toStringArray(array);
            assertThat(result).isEmpty();
        } finally {
            array.close();
        }
    }

    @Test
    void toDispatchArrayUnpacksRealSafeArrayOfDispatchObjects() throws Exception {
        Factory factory = new Factory();
        try {
            factory.getComThread().execute(() -> {
                Dictionary dictionary = factory.createObject(Dictionary.class);
                Dispatch dispatch = (Dispatch) dictionary.getRawDispatch();

                SAFEARRAY.ByReference array = SAFEARRAY.createSafeArray(
                    new com.sun.jna.platform.win32.WTypes.VARTYPE(Variant.VT_DISPATCH), 1);
                try {
                    array.putElement(dispatch, 0);

                    List<Dictionary> result = ComArrays.toDispatchArray(array, Dictionary.class, factory);

                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).getRawDispatch()).isNotNull();
                } finally {
                    array.close();
                }
                return null;
            });
        } finally {
            factory.getComThread().terminate(1000);
        }
    }
}
