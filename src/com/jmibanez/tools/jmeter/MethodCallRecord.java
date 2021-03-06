package com.jmibanez.tools.jmeter;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import static com.jmibanez.tools.jmeter.util.ArgumentsUtil.packArgs;
import static com.jmibanez.tools.jmeter.util.ArgumentsUtil.unpackArgs;


public class MethodCallRecord
    implements Serializable
{

    private static final long serialVersionUID = -30090001L;

    private int index;
    private String target;
    private String method;
    private Class<?>[] argTypes;
    private Object[] args;
    private byte[] argsPacked;
    private String mangledMethodName;
    private String mangledArgs;
    private Object returnValue;
    private Throwable returnException;
    private boolean isException = false;

    private transient boolean isRemoteReturned = false;
    private transient Map<String, String> remotePathsInReturn = Collections.emptyMap();

    MethodCallRecord() {
    }

    public MethodCallRecord(final int index, final String target, final Method m,
                            final Object[] args) {
        this.index = index;
        this.target = target;
        this.argTypes = m.getParameterTypes();
        this.method = m.getName();
        String[] builtNames = constructMethodName(m.getName(), this.argTypes);
        this.mangledMethodName = builtNames[0];
        this.mangledArgs = builtNames[1];
        this.args = args;
        this.argsPacked = packArgs(this.args);
    }

    public int getIndex() {
        return index;
    }

    public String getTarget() {
        return target;
    }

    public String getMethod() {
        return method;
    }

    public String getMangledMethodName() {
        return mangledMethodName;
    }

    public String getMangledArguments() {
        return mangledArgs;
    }

    public Object[] recreateArguments() {
        this.args = unpackArgs(this.argsPacked);
        return args;
    }

    public Object[] getArguments() {
        return args;
    }

    public Class<?>[] getArgumentTypes() {
        return argTypes;
    }

    public void returned(Object returnValue) {
        this.returnValue = returnValue;
    }

    public void thrown(Throwable t) {
        this.returnValue = t;
        this.isException = true;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public Throwable getReturnValueAsThrowable() {
        if(isException) {
            return (Throwable) returnValue;
        }
        else {
            throw new IllegalStateException("Not an exception");
        }
    }

    public boolean isException() {
        return isException;
    }

    public void setRemoteReturned(final boolean isRemoteReturned) {
        this.isRemoteReturned = isRemoteReturned;
    }

    public boolean isRemoteReturned() {
        return isRemoteReturned;
    }

    public void setRemotePathsInReturn(final Map<String, String> remotePathsInReturn) {
        this.remotePathsInReturn = remotePathsInReturn;
    }

    public Map<String, String> getRemotePathsInReturn() {
        return remotePathsInReturn;
    }

    public static String[] constructMethodName(String methodName, Class<?>[] argTypes) {
        StringBuilder full = new StringBuilder();
        StringBuilder args = new StringBuilder();

        if(argTypes == null || argTypes.length == 0) {
            return new String[] { methodName + ":", "" };
        }

        full.append(methodName);
        full.append(":");
        for(Class<?> c : argTypes) {
            args.append(c.getName());
            args.append(",");
        }

        args.deleteCharAt(args.length() - 1);

        full.append(args);
        return new String[] { full.toString(), args.toString() };
    }

    private void writeObject(ObjectOutputStream out)
        throws IOException {
        // Custom format, to allow packed argument values
        out.writeUTF("CALL");

        out.writeUTF(method);

        out.writeInt(argsPacked.length);
        out.write(argsPacked);

        out.writeUTF("RETURN");

        out.writeBoolean(isException);
        out.writeObject(returnValue);

        out.writeUTF("END");
    }

    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        // Custom format, to allow packed argument values
        String head = in.readUTF();
        if(!"CALL".equals(head)) {
            throw new IllegalStateException("Invalid state in input stream: Object header not found");
        }

        method = in.readUTF();

        int packLen = in.readInt();
        argsPacked = new byte[packLen];
        in.read(argsPacked, 0, packLen);

        String ret = in.readUTF();
        if(!"RETURN".equals(ret)) {
            throw new IllegalStateException("Invalid state in input stream: Return value header not found");
        }

        isException = in.readBoolean();
        returnValue = in.readObject();

        String eof = in.readUTF();
        if(!"END".equals(eof)) {
            throw new IllegalStateException("Invalid state in input stream: End of stream not found");
        }

        this.args = unpackArgs(this.argsPacked);
    }
}

