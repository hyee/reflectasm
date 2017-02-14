package com.esotericsoftware.reflectasm;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

/**
 * Created by Will on 2017/2/12.
 */
public class LambdaAccess extends ClassAccess {
    protected LambdaAccess(Accessor accessor) {
        super(accessor);
    }

    public CallSite getCallsite(int index, String type) {
        MethodHandle handle=getHandleWithIndex(index,type);
        return null;
    }
}
