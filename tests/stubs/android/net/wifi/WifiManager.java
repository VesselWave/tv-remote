package android.net.wifi;
public class WifiManager { public class MulticastLock { public void acquire() {} public void release() {} }
  public MulticastLock createMulticastLock(String t) { return new MulticastLock(); } }
