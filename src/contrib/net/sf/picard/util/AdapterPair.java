package contrib.net.sf.picard.util;

public interface AdapterPair {

    String get3PrimeAdapter();
    String get3PrimeAdapterInReadOrder();
    byte[] get3PrimeAdapterBytes();
    byte[] get3PrimeAdapterBytesInReadOrder();

    String get5PrimeAdapter();
    String get5PrimeAdapterInReadOrder();
    byte[] get5PrimeAdapterBytes();
    byte[] get5PrimeAdapterBytesInReadOrder();

    String getName();
}
