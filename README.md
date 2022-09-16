# Speed Dial

Simple annotation processor based on Franz work on netty performance
e.g. https://github.com/netty/netty/pull/12806

In particular, [this tweet](https://twitter.com/richardstartin/status/1570430298072162308) got me thinking:
_What if the diff itself was simpler?_

See also: https://bugs.openjdk.org/browse/JDK-8180450

# Usage

```java
interface MyInterface extends CharSequence {
  @SpeedDial(target = CharSequence.class, common = Impl.class)
  int someMethod(long arg1, String arg2);
}
```

Produces a generated `MyInterfaceSpeedDialer` utility class with static methods, such that invocations
may be made thusly:

```java
void demo(CharSequence iface) {
    return MyInterfaceSpeedDialer.someMethod(iface, 1L, "str");
}
```

using a generated method along these lines:
```java
@Generated
public static final class MyInterfaceSpeedDialer {
    private MyInterfaceSpeedDialer() {}

    public static int someMethod(CharSequence delegate, long arg1, String arg2) {
        if (delegate instanceof Impl) {
            return ((Impl) delegate).someMethod(arg1, arg2);
        }
        return ((MyInterface) delegate).someMethod(arg1, arg2);
    }
}
```

Where the invocation itself attempts to do an instanceof check against `Impl`
before falling back to an unchecked cast to `MyInterface`.