package lean.tvremote;

/** Prints the register manifest the app sends, for scripts/ssap_probe.py. */
public final class DumpManifest {
    public static void main(String[] args) throws Exception {
        System.out.println(Tv.Manifest.lgMobile().toString());
    }
}
