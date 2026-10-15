package android.text;
public final class TextUtils { public static String join(CharSequence d, Iterable<?> t) { StringBuilder sb = new StringBuilder(); for (Object o : t) { if (sb.length() > 0) sb.append(d); sb.append(o); } return sb.toString(); } }
