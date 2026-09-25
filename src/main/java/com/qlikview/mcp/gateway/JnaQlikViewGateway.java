package com.qlikview.mcp.gateway;

import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.com.ChartDimension;
import com.qlikview.mcp.gateway.com.ChartDimensions;
import com.qlikview.mcp.gateway.com.ChartExpressions;
import com.qlikview.mcp.gateway.com.ChartProperties;
import com.qlikview.mcp.gateway.com.ComArrays;
import com.qlikview.mcp.gateway.com.Document;
import com.qlikview.mcp.gateway.com.FieldDescription;
import com.qlikview.mcp.gateway.com.FieldDescriptions;
import com.qlikview.mcp.gateway.com.QlikView;
import com.qlikview.mcp.gateway.com.Sheet;
import com.qlikview.mcp.gateway.com.SheetObject;
import com.qlikview.mcp.gateway.com.VariableDescriptions;
import com.sun.jna.platform.win32.COM.util.ComThread;
import com.sun.jna.platform.win32.COM.util.Factory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Read-only bridge to a running QlikView Desktop instance via in-process COM automation (JNA).
 * Every call this class makes runs on {@link Factory}'s dedicated COM thread and is bounded by
 * {@code qlikview.call.timeout} (enforced by JNA's {@link ComThread#execute}, not code here).
 * <p>
 * A COM apartment thread cannot be force-killed: if a call hangs (QlikView's COM layer does not
 * always fail fast - see spike 0.9), the underlying thread stays stuck forever, and every future
 * call on the same {@link Factory} would queue behind it. To recover, a timeout discards the
 * current {@link Factory} entirely and lazily builds a fresh one (fresh COM thread, fresh
 * {@code CoInitializeEx}) for the next call - the stuck thread is simply abandoned, never reused.
 */
public class JnaQlikViewGateway implements QlikViewGateway {

    private final QlikViewProperties properties;
    private final AtomicReference<Factory> factory = new AtomicReference<>();

    public JnaQlikViewGateway(QlikViewProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getScript(Path document) {
        return call((qv, f) -> activeOrOpenDocument(qv, document).getProperties().getScript());
    }

    @Override
    public VariableDescription[] getVariables(Path document) {
        return call((qv, f) -> {
            VariableDescriptions descriptions = activeOrOpenDocument(qv, document).getVariableDescriptions();
            int count = descriptions.getCount();
            VariableDescription[] result = new VariableDescription[count];
            for (int i = 0; i < count; i++) {
                com.qlikview.mcp.gateway.com.VariableDescription d = descriptions.item(i);
                result[i] = new VariableDescription(d.getName(), d.getRawValue(), d.getIsReserved());
            }
            return result;
        });
    }

    @Override
    public DataModel getDataModel(Path document) {
        return call((qv, f) -> {
            Document doc = activeOrOpenDocument(qv, document);

            int tableCount = doc.getTableCount();
            TableInfo[] tables = new TableInfo[tableCount];
            for (int i = 0; i < tableCount; i++) {
                tables[i] = new TableInfo(doc.getTableName(i));
            }

            FieldDescriptions fieldDescriptions = doc.getFieldDescriptions();
            int fieldCount = fieldDescriptions.getCount();
            FieldInfo[] fields = new FieldInfo[fieldCount];
            for (int i = 0; i < fieldCount; i++) {
                FieldDescription fd = fieldDescriptions.item(i);
                fields[i] = new FieldInfo(
                    fd.getName(), fd.getCardinal(), fd.getIsSystem(), fd.getIsNumeric(),
                    ComArrays.toStringArray(fd.getSrcTables()));
            }

            return new DataModel(tables, fields);
        });
    }

    @Override
    public SheetInfo[] getSheets(Path document) {
        return call((qv, f) -> {
            Document doc = activeOrOpenDocument(qv, document);
            List<Sheet> sheets = ComArrays.toDispatchArray(doc.getSheetsAll(), Sheet.class, f);

            SheetInfo[] result = new SheetInfo[sheets.size()];
            for (int i = 0; i < sheets.size(); i++) {
                Sheet sheet = sheets.get(i);
                String caption = sheet.getProperties().getName();
                List<SheetObject> objects = ComArrays.toDispatchArray(sheet.getSheetObjects(), SheetObject.class, f);

                SheetObjectInfo[] objectInfos = new SheetObjectInfo[objects.size()];
                for (int j = 0; j < objects.size(); j++) {
                    SheetObject obj = objects.get(j);
                    objectInfos[j] = new SheetObjectInfo(obj.getObjectId(), String.valueOf(obj.getObjectType()));
                }
                result[i] = new SheetInfo(caption, objectInfos);
            }
            return result;
        });
    }

    @Override
    public ObjectDetail getObject(Path document, String objectId) {
        return call((qv, f) -> {
            Document doc = activeOrOpenDocument(qv, document);
            List<Sheet> sheets = ComArrays.toDispatchArray(doc.getSheetsAll(), Sheet.class, f);

            SheetObject found = null;
            outer:
            for (Sheet sheet : sheets) {
                List<SheetObject> objects = ComArrays.toDispatchArray(sheet.getSheetObjects(), SheetObject.class, f);
                for (SheetObject obj : objects) {
                    if (objectId.equals(obj.getObjectId())) {
                        found = obj;
                        break outer;
                    }
                }
            }

            if (found == null) {
                throw new GatewayException("Object not found on any sheet: " + objectId);
            }

            String objectType = String.valueOf(found.getObjectType());
            List<String> dimensions = new ArrayList<>();
            List<String> expressions = new ArrayList<>();

            // Only chart-type objects (GraphProperties) expose Dimensions/Expressions - other
            // object types (current-selections box, search object, ...) throw when accessed.
            try {
                ChartProperties chartProperties = found.getProperties();
                ChartDimensions dims = chartProperties.getDimensions();
                for (int i = 0; i < dims.getCount(); i++) {
                    ChartDimension dim = dims.item(i);
                    dimensions.add(dim.getPseudoDef().getName());
                }
                ChartExpressions exprs = chartProperties.getExpressions();
                for (int i = 0; i < exprs.getCount(); i++) {
                    expressions.add(exprs.item(i).item(0).getData().getExpressionData().getDefinition().getV());
                }
            } catch (RuntimeException e) {
                // Not a chart-type object; dimensions/expressions stay empty.
            }

            return new ObjectDetail(objectId, objectType, dimensions.toArray(new String[0]), expressions.toArray(new String[0]));
        });
    }

    @Override
    public String evaluate(Path document, String expression) {
        return call((qv, f) -> activeOrOpenDocument(qv, document).evaluate(expression));
    }

    private Document activeOrOpenDocument(QlikView qv, Path document) {
        String documentPath = document.toString();
        Document active = qv.activeDocument();
        if (active != null && documentPath.equalsIgnoreCase(active.getProperties().getFileName())) {
            return active;
        }
        Document opened = qv.openDoc(documentPath, "", "", "");
        if (opened == null) {
            throw new GatewayException("Could not open or access document: " + documentPath);
        }
        return opened;
    }

    private <T> T call(ComCall<T> action) {
        Factory f = currentFactory();
        try {
            QlikView qv = f.createObject(QlikView.class);
            return action.call(qv, f);
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                factory.compareAndSet(f, null);
                throw new GatewayException(
                    "QlikView call timed out after " + properties.call().timeout()
                        + ". QlikView Desktop may be showing a blocked dialog on this document; "
                        + "check the Desktop session before retrying.", e);
            }
            throw new GatewayException("QlikView COM call failed: " + e.getMessage(), e);
        }
    }

    private Factory currentFactory() {
        Factory existing = factory.get();
        if (existing != null) {
            return existing;
        }
        Factory created = new Factory(new ComThread(
            "qlikview-mcp-com-thread",
            properties.call().timeout().toMillis(),
            (thread, throwable) -> { /* surfaced to the caller via the failed call's exception */ }));
        factory.compareAndSet(null, created);
        return factory.get();
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface ComCall<T> {
        T call(QlikView qv, Factory factory);
    }
}
