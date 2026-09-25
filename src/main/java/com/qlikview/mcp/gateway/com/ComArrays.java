package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.Dispatch;
import com.sun.jna.platform.win32.COM.util.Factory;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Some QlikView automation members return a native array (a {@code VT_ARRAY} VARIANT, e.g.
 * {@code GetSheetsAll()}, {@code GetSheetObjects()}, {@code FieldDescription.SrcTables}) rather
 * than a {@code Count}/{@code Item(i)}-style collection object. JNA's declared-return-type
 * conversion resolves such a member to a raw {@link SAFEARRAY} when the interface method is
 * declared {@code Object} (see the JNA COM interfaces in this package for which members these
 * are) - this class unpacks that SAFEARRAY into a plain Java array/list.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ComArrays {

    /**
     * Unpacks a {@code VT_ARRAY} of {@code VT_BSTR} (e.g. {@code FieldDescription.SrcTables})
     * into a {@code String[]}.
     */
    public static String[] toStringArray(Object safeArrayValue) {
        if (safeArrayValue == null) {
            return new String[0];
        }
        SAFEARRAY array = (SAFEARRAY) safeArrayValue;
        int count = elementCount(array);
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = (String) array.getElement(i);
        }
        return result;
    }

    /**
     * Unpacks a {@code VT_ARRAY} of {@code VT_DISPATCH} (e.g. {@code GetSheetsAll()},
     * {@code GetSheetObjects()}) into a list of proxies of the given interface type, using the
     * same {@link Factory} that created the object this array came from.
     */
    public static <T> List<T> toDispatchArray(Object safeArrayValue, Class<T> comInterface, Factory factory) {
        if (safeArrayValue == null) {
            return List.of();
        }
        SAFEARRAY array = (SAFEARRAY) safeArrayValue;
        int count = elementCount(array);
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Dispatch dispatch = (Dispatch) array.getElement(i);
            result.add(factory.createProxy(comInterface, dispatch));
        }
        return result;
    }

    private static int elementCount(SAFEARRAY array) {
        return array.rgsabound.length == 0 ? 0 : array.rgsabound[0].cElements.intValue();
    }
}
