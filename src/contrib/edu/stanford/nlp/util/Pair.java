package contrib.edu.stanford.nlp.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Pair is a Class for holding mutable pairs of objects.
 *
 * <i>Implementation note:</i>
 * on a 32-bit JVM uses ~ 8 (this) + 4 (first) + 4 (second) = 16 bytes.
 * on a 64-bit JVM uses ~ 16 (this) + 8 (first) + 8 (second) = 32 bytes.
 *
 * Many applications use a lot of Pairs so it's good to keep this
 * number small.
 *
 * @author Dan Klein
 * @author Christopher Manning (added stuff from Kristina's, rounded out)
 * @version 2002/08/25
 */

public class Pair <T1,T2> implements Comparable<Pair<T1,T2>>, Serializable {

  /**
   * Direct access is deprecated.  Use first().
   *
   * @serial
   */
  private int first;

  /**
   * Direct access is deprecated.  Use second().
   *
   * @serial
   */
  private int second;

  public Pair() {
    // first = null; second = null; -- default initialization
  }

  public Pair(T1 first, T2 second) {
    this.setFirst(first);
    this.setSecond(second);
  }

  public T1 first() {
    return getFirst();
  }

  public T2 second() {
    return getSecond();
  }

  public void setFirst(T1 o) {
    first = (Integer) o;
  }

  public void setSecond(T2 o) {
    second = (Integer) o;
  }

  @Override
  public String toString() {
    return "(" + getFirst() + "," + getSecond() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Pair) {
      @SuppressWarnings("rawtypes")
      Pair p = (Pair) o;
      return (getFirst() == null ? p.first() == null : getFirst().equals(p.first())) && (getSecond() == null ? p.second() == null : getSecond().equals(p.second()));
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int firstHash  = (getFirst() == null ? 0 : getFirst().hashCode());
    int secondHash = (getSecond() == null ? 0 : getSecond().hashCode());

    return firstHash*31 + secondHash;
  }

  public List<Object> asList() {
    return CollectionUtils.makeList(getFirst(), getSecond());
  }

  /**
   * Read a string representation of a Pair from a DataStream.
   * This might not work correctly unless the pair of objects are of type
   * <code>String</code>.
   */
  public static Pair<String, String> readStringPair(DataInputStream in) {
    Pair<String, String> p = new Pair<String, String>();
    try {
      p.setFirst(in.readUTF());
      p.setSecond(in.readUTF());
    } catch (Exception e) {
      e.printStackTrace();
    }
    return p;
  }

  /**
   * Returns a Pair constructed from X and Y.  Convenience method; the
   * compiler will disambiguate the classes used for you so that you
   * don't have to write out potentially long class names.
   */
  public static <X, Y> Pair<X, Y> makePair(X x, Y y) {
    return new Pair<X, Y>(x, y);
  }

  /**
   * Write a string representation of a Pair to a DataStream.
   * The <code>toString()</code> method is called on each of the pair
   * of objects and a <code>String</code> representation is written.
   * This might not allow one to recover the pair of objects unless they
   * are of type <code>String</code>.
   */
  public void save(DataOutputStream out) {
    try {
      out.writeUTF(getFirst().toString());
      out.writeUTF(getSecond().toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Compares this <code>Pair</code> to another object.
   * If the object is a <code>Pair</code>, this function will work providing
   * the elements of the <code>Pair</code> are themselves comparable.
   * It will then return a value based on the pair of objects, where
   * <code>p &gt; q iff p.first() &gt; q.first() ||
   * (p.first().equals(q.first()) && p.second() &gt; q.second())</code>.
   * If the other object is not a <code>Pair</code>, it throws a
   * <code>ClassCastException</code>.
   *
   * @param another the <code>Object</code> to be compared.
   * @return the value <code>0</code> if the argument is a
   *         <code>Pair</code> equal to this <code>Pair</code>; a value less than
   *         <code>0</code> if the argument is a <code>Pair</code>
   *         greater than this <code>Pair</code>; and a value
   *         greater than <code>0</code> if the argument is a
   *         <code>Pair</code> less than this <code>Pair</code>.
   * @throws ClassCastException if the argument is not a
   *                            <code>Pair</code>.
   * @see java.lang.Comparable
   */
  @Override
	@SuppressWarnings("unchecked")
  public int compareTo(Pair<T1,T2> another) {
    if (first() instanceof Comparable) {
      int comp = ((Comparable<T1>) first()).compareTo(another.first());
      if (comp != 0) {
        return comp;
      }
    }

    if (second() instanceof Comparable) {
      return ((Comparable<T2>) second()).compareTo(another.second());
    }

    if ((!(first() instanceof Comparable)) && (!(second() instanceof Comparable))) {
      throw new AssertionError("Neither element of pair comparable");
    }

    return 0;
  }

  /**
   * If first and second are Strings, then this returns an MutableInternedPair
   * where the Strings have been interned, and if this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   *
   * @param p A pair of Strings
   * @return MutableInternedPair, with same first and second as this.
   */
  public static Pair<String, String> stringIntern(Pair<String, String> p) {
    return new MutableInternedPair(p);
  }

  /**
   * Returns an MutableInternedPair where the Strings have been interned.
   * This is a factory method for creating an
   * MutableInternedPair.  It requires the arguments to be Strings.
   * If this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   * <p><i>Note:</i> I put this in thinking that its use might be
   * faster than calling <code>x = new Pair(a, b).stringIntern()</code>
   * but it's not really clear whether this is true.
   *
   * @param first  The first object
   * @param second The second object
   * @return An MutableInternedPair, with given first and second
   */
  public static Pair<String, String> internedStringPair(String first, String second) {
    return new MutableInternedPair(first, second);
  }


  public T1 getFirst() {
		return (T1) (Integer) first;
	}


	public T2 getSecond() {
		return (T2) (Integer) second;
	}


	/**
   * use serialVersionUID for cross version serialization compatibility
   */
  private static final long serialVersionUID = 1360822168806852921L;


  static class MutableInternedPair extends Pair<String, String> {

    private MutableInternedPair(Pair<String, String> p) {
      super(p.getFirst(), p.getSecond());
      internStrings();
    }

    private MutableInternedPair(String first, String second) {
      super(first, second);
      internStrings();
    }

    protected Object readResolve() {
      internStrings();
      return this;
    }

    private void internStrings() {
      if (getFirst() != null) {
        setFirst(getFirst().intern());
      }
      if (getSecond() != null) {
        setSecond(getSecond().intern());
      }
    }

    // use serialVersionUID for cross version serialization compatibility
    private static final long serialVersionUID = 1360822168806852922L;

  }

  
  /**
   * Compares a <code>Pair</code> to another <code>Pair</code> according to the first object of the pair only
   * This function will work providing
   * the first element of the <code>Pair</code> is comparable, otherwise will throw a 
   * <code>ClassCastException</code>
   * @author jonathanberant
   *
   * @param <T1>
   * @param <T2>
   */
  public static class ByFirstPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return ((Comparable<T1>) pair1.first()).compareTo(pair2.first());
    }
  }
  
  /**
   * Compares a <code>Pair</code> to another <code>Pair</code> according to the first object of the pair only in decreasing order
   * This function will work providing
   * the first element of the <code>Pair</code> is comparable, otherwise will throw a 
   * <code>ClassCastException</code>
   * @author jonathanberant
   *
   * @param <T1>
   * @param <T2>
   */
  public static class ByFirstReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return -((Comparable<T1>) pair1.first()).compareTo(pair2.first());
    }
  }
  
  /**
   * Compares a <code>Pair</code> to another <code>Pair</code> according to the second object of the pair only
   * This function will work providing
   * the first element of the <code>Pair</code> is comparable, otherwise will throw a 
   * <code>ClassCastException</code>
   * @author jonathanberant
   *
   * @param <T1>
   * @param <T2>
   */
  public static class BySecondPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return ((Comparable<T2>) pair1.second()).compareTo(pair2.second());
    }
  }
  
  /**
   * Compares a <code>Pair</code> to another <code>Pair</code> according to the second object of the pair only in decreasing order
   * This function will work providing
   * the first element of the <code>Pair</code> is comparable, otherwise will throw a 
   * <code>ClassCastException</code>
   * @author jonathanberant
   *
   * @param <T1>
   * @param <T2>
   */
  public static class BySecondReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return -((Comparable<T2>) pair1.second()).compareTo(pair2.second());
    }
  }
  
}
