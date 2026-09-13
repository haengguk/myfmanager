package com.lolfm.career;

import java.util.*;
import java.util.function.Supplier;

/** Exact immutable input reuse within one command/read. Never skips a database read or integrity comparison. */
final class CareerReadScope implements AutoCloseable {
    private static final ThreadLocal<CareerReadScope> CURRENT=new ThreadLocal<>();
    private static final int LIMIT=64;
    private final Map<Object,Object> values=new HashMap<>();
    private final CareerReadScope parent;
    private final String career;
    private final boolean owner;
    private record JsonKey(String json,Class<?> type) {}
    private final Map<String,String> digests=new HashMap<>();
    static String digest(String json,Supplier<String> compute){
        var scope=CURRENT.get();if(scope==null||json.length()<4096)return compute.get();
        String value=scope.digests.get(json);if(value!=null)return value;
        value=compute.get();if(scope.digests.size()>=16)scope.digests.clear();
        scope.digests.put(json,value);return value;
    }
    private record ProjectionKey(Object source,Object state,Object extra) {
        @Override public int hashCode(){return 31*System.identityHashCode(source)+System.identityHashCode(state)+Objects.hashCode(extra);}
        @Override public boolean equals(Object o){return o instanceof ProjectionKey k&&source==k.source&&state==k.state&&Objects.equals(extra,k.extra);}
    }
    private CareerReadScope(String career){this.career=career;parent=CURRENT.get();owner=parent==null||!parent.career.equals(career);if(owner)CURRENT.set(this);}
    static CareerReadScope open(String career){return new CareerReadScope(career);}
    static <T>T immutable(String json,Class<T> type,Supplier<T> decode){
        // JsonNode, mutable commands and engines deliberately never enter the cache.
        if(type!=CareerRosterStore.Directory.class&&type!=CareerMarketState.class
                &&type!=CareerDevelopmentState.class&&type!=CareerLifecycleState.class)return decode.get();
        return memo(new JsonKey(json,type),decode);
    }
    static <T>T projection(Object source,Object state,Object extra,Supplier<T> compute){return memo(new ProjectionKey(source,state,extra),compute);}
    @SuppressWarnings("unchecked") private static <T>T memo(Object key,Supplier<T> compute){
        var scope=CURRENT.get();if(scope==null)return compute.get();
        Object existing=scope.values.get(key);if(existing!=null)return (T)existing;
        T result=compute.get();if(scope.values.size()>=LIMIT)scope.values.clear();scope.values.put(key,result);return result;
    }
    @Override public void close(){if(owner){values.clear();digests.clear();if(parent==null)CURRENT.remove();else CURRENT.set(parent);}}
}
