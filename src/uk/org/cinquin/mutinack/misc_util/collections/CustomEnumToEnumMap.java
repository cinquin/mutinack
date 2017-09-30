package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import jdk.internal.misc.SharedSecrets;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * WARNING: WORK IN PROGRESS. PROBABLY DOES NOT FULLY IMPLEMENT THE MAP CONTRACT YET
 *
 * This is a customized version of EnumMap provided by JDK 1.8, applicable to the case
 * Map<Enum, Enum>, and memory-optimized to store all of the mappings into a single long
 * primitive. Static methods are provided by extending classes that make it possible to
 * store full maps as longs instead of objects.
 *
 * This class is based in part on CustomEnumMap by Josh Bloch.
 *
 * @author Josh Bloch
 * @author Olivier Cinquin
 */
public abstract class CustomEnumToEnumMap<K extends Enum<K>, V extends Enum<V>> implements Map<K, V>,
	java.io.Serializable, Cloneable
{
	private long values1;

	protected abstract int getStride();

	protected static final long computeUnshiftedMask(int index, int stride) {
		long mask = 1;
		for (int i = 0; i < stride - 1; i++) {
			mask = mask | (mask << 1);
		}
		return mask;
	}

	@SuppressWarnings("static-method")
	protected long getUnshiftedMask(int index, int stride) {
		return computeUnshiftedMask(index, stride);
	}

	protected long getShiftedMask(int index, int stride) {
		return getUnshiftedMask(index, stride) << (index * stride);
	}

	protected final static long setValue(long codedMap, int index, final long value, final int stride,
			final long shiftedMask) {

		if (value > (1 << stride) - 2) {
			throw new IllegalArgumentException();
		}

		if (index * stride < 64) {
			codedMap = codedMap & (~shiftedMask);
			codedMap = codedMap | (value << (index * stride));
		} else {
			Assert.isTrue(false);
			/*index -= 20;
			values2 = values2 & (~mask);
			values2 = values2 | (value << (index * stride));*/
		}
		return codedMap;
	}

	//AtomicInteger counter = new AtomicInteger();

	private void enter() {
		/*if (counter.incrementAndGet() > 1) {
			throw new AssertionFailedException();
		}*/
	}

	private void leave() {
		//counter.decrementAndGet();
	}

	private void setValue(int index, final long value, final int stride) {
		enter();
		values1 = setValue(values1, index, value, stride, getShiftedMask(index, stride));
		leave();
	}

	protected final static long getValue(long codedMap, int index, final int stride, long unshiftedMask) {
		if (index * stride < 64) {
			return (codedMap >> (index * stride)) & unshiftedMask;
		} else {
			throw new AssertionFailedException();
			/*index -= 20;
			return (values2 >> (index * stride)) & getUnshiftedMask(index, stride);*/
		}
	}

	private int getValueAtIndex(int keyIndex) {
		enter();
		try {
			return (int) getValue(values1, keyIndex, getStride(), getUnshiftedMask(keyIndex, getStride()));
		} finally {
			leave();
		}
	}

	private V getValue(int keyIndex) {
		int valueIndex = getValueAtIndex(keyIndex) - 1;
		enter();
		try {
			if (valueIndex == -1) {
				return null;
			} else {
				return getValueUniverse()[valueIndex];
			}
		} finally {
			leave();
		}
	}

	private void setValue(int keyIndex, long value) {
		setValue(keyIndex, value, getStride());
	}

	/**
	 * Distinguished non-null value for representing null values.
	 */
	private static final Object NULL = new Object() {
		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public String toString() {
			return "java.util.EnumMap.NULL";
		}
	};

	@SuppressWarnings("unused")
	private static Object maskNull(Object value) {
		return (value == null ? NULL : value);
	}

	@SuppressWarnings("unchecked")
	private V unmaskNull(Object value) {
		return (V)(value == NULL ? null : value);
	}

	/**
	 * Creates an empty enum map with the specified key type.
	 *
	 * @param keyType the class object of the key type for this enum map
	 * @throws NullPointerException if <tt>keyType</tt> is null
	 */
	public CustomEnumToEnumMap(Class<K> keyType, Class<V> valueType) {
		setKeyType(keyType);
		setValueType(valueType);
		setKeyUniverse(getUniverse(keyType));
		setValueUniverse(getUniverse(valueType));
		if (getKeyUniverse().length * (getValueUniverse().length + 1) > 128) {
			throw new IllegalArgumentException("Too many combinations");
		}
	}

	/**
	 * Creates an enum map with the same key type as the specified enum
	 * map, initially containing the same mappings (if any).
	 *
	 * @param m the enum map from which to initialize this enum map
	 * @throws NullPointerException if <tt>m</tt> is null
	 */
	@SuppressWarnings("unchecked")
	public CustomEnumToEnumMap(CustomEnumToEnumMap<K, ? extends V> m) {
		setKeyType(m.getKeyType());
		setValueType((Class<V>) m.getValueType());
		setKeyUniverse(m.getKeyUniverse());
		setValueUniverse(m.getValueUniverse());
		values1 = m.values1;
		//values2 = m.values2;
		//setSize(m.getSize());
	}

	/**
	 * Creates an enum map initialized from the specified map.  If the
	 * specified map is an <tt>EnumMap</tt> instance, this constructor behaves
	 * identically to {@link #CustomEnumToEnumMap(CustomEnumToEnumMap)}.  Otherwise, the specified map
	 * must contain at least one mapping (in order to determine the new
	 * enum map's key type).
	 *
	 * @param m the map from which to initialize this enum map
	 * @throws IllegalArgumentException if <tt>m</tt> is not an
	 *     <tt>EnumMap</tt> instance and contains no mappings
	 * @throws NullPointerException if <tt>m</tt> is null
	 */
	@SuppressWarnings("unchecked")
	public CustomEnumToEnumMap(Map<K, ? extends V> m) {
		if (m instanceof CustomEnumToEnumMap) {
			CustomEnumToEnumMap<K, ? extends V> em = (CustomEnumToEnumMap<K, ? extends V>) m;
			setKeyType(em.getKeyType());
			setValueType((Class<V>) em.getValueType());
			setKeyUniverse(em.getKeyUniverse());
			setValueUniverse(em.getValueUniverse());
			values1 = em.values1;
			//values2 = em.values2;
			//setSize(em.getSize());
		} else {
			if (m.isEmpty())
				throw new IllegalArgumentException("Specified map is empty");
			setKeyType(m.keySet().iterator().next().getDeclaringClass());
			setValueType(m.values().iterator().next().getDeclaringClass());
			setKeyUniverse(getUniverse(getKeyType()));
			setValueUniverse(getUniverse(getValueType()));
			if (getKeyUniverse().length * (getValueUniverse().length + 1) > 64) {
				throw new IllegalArgumentException("Too many combinations");
			}
			putAll(m);
		}
	}

	@Override
	public final void forEach(BiConsumer<? super K, ? super V> action) {
		Objects.requireNonNull(action);
		for (int i = 0; i < getKeyUniverse().length; i++) {
			int valueIndex = getValueAtIndex(i) - 1;
			if (valueIndex >= 0) {
				action.accept(getKeyUniverse()[i], getValueUniverse()[valueIndex]);
			}
		}
	}

	// Query Operations

	/**
	 * Returns the number of key-value mappings in this map.
	 *
	 * @return the number of key-value mappings in this map
	 */
	@Override
	public final int size() {
		return getSize();
	}

	/**
	 * Returns <tt>true</tt> if this map maps one or more keys to the
	 * specified value.
	 *
	 * @param value the value whose presence in this map is to be tested
	 * @return <tt>true</tt> if this map maps one or more keys to this value
	 */
	@Override
	public final boolean containsValue(Object value) {

		for (int i = 0; i < getKeyUniverse().length; i++) {
			if (getValue(i) == value) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns <tt>true</tt> if this map contains a mapping for the specified
	 * key.
	 *
	 * @param key the key whose presence in this map is to be tested
	 * @return <tt>true</tt> if this map contains a mapping for the specified
	 *            key
	 */
	@Override
	public final boolean containsKey(Object key) {
		return isValidKey(key) && getValueAtIndex(((Enum<?>)key).ordinal()) != 0;
	}

	private boolean containsMapping(Object key, Object value) {
		return isValidKey(key) && get(key) == value;
	}

	private int getKetIndex(K key) {
		return ((Enum<?>)key).ordinal();
	}

	/**
	 * Returns the value to which the specified key is mapped,
	 * or {@code null} if this map contains no mapping for the key.
	 *
	 * <p>More formally, if this map contains a mapping from a key
	 * {@code k} to a value {@code v} such that {@code (key == k)},
	 * then this method returns {@code v}; otherwise it returns
	 * {@code null}.  (There can be at most one such mapping.)
	 *
	 * <p>A return value of {@code null} does not <i>necessarily</i>
	 * indicate that the map contains no mapping for the key; it's also
	 * possible that the map explicitly maps the key to {@code null}.
	 * The {@link #containsKey containsKey} operation may be used to
	 * distinguish these two cases.
	 */
	@Override
	public final V get(Object key) {
		if (!isValidKey(key)) {
			return null;
		}
		@SuppressWarnings("unchecked")
		int valueIndex = getValueAtIndex(getKetIndex((K) key));
		if (valueIndex == 0) {
			return null;
		}
		return getValueUniverse()[valueIndex - 1];
	}

	// Modification Operations

	/**
	 * Associates the specified value with the specified key in this map.
	 * If the map previously contained a mapping for this key, the old
	 * value is replaced.
	 *
	 * @param key the key with which the specified value is to be associated
	 * @param value the value to be associated with the specified key
	 *
	 * @return the previous value associated with specified key, or
	 *     <tt>null</tt> if there was no mapping for key.  (A <tt>null</tt>
	 *     return can also indicate that the map previously associated
	 *     <tt>null</tt> with the specified key.)
	 * @throws NullPointerException if the specified key is null
	 */
	@Override
	public final V put(K key, V value) {
		typeCheck(key);

		int keyIndex = key.ordinal();
		int oldValueIndex = getValueAtIndex(keyIndex);
		setValueAtIndex(keyIndex, value);
		/*
		if (oldValueIndex == 0 && value != null)
			setSize(getSize() + 1);
		if (oldValueIndex > 0 && value == null) {
			setSize(getSize() - 1);
		}*/
		return oldValueIndex == 0 ? null : getValueUniverse()[oldValueIndex - 1];
	}

	protected final static<V extends Enum<V>> int valueToInt(V value) {
		return value == null ? 0 : value.ordinal() + 1;
	}

	protected final void setValueAtIndex(int keyIndex, V value) {
		setValue(keyIndex, valueToInt(value));
	}

	/**
	 * Removes the mapping for this key from this map if present.
	 *
	 * @param key the key whose mapping is to be removed from the map
	 * @return the previous value associated with specified key, or
	 *     <tt>null</tt> if there was no entry for key.  (A <tt>null</tt>
	 *     return can also indicate that the map previously associated
	 *     <tt>null</tt> with the specified key.)
	 */
	@Override
	public final V remove(Object key) {
		if (!isValidKey(key))
			return null;
		int keyIndex = ((Enum<?>)key).ordinal();
		V oldValue = getValue(keyIndex);
		setValue(keyIndex, 0);
		/*if (oldValue != null)
			setSize(getSize() - 1);*/
		return oldValue;
	}

	private boolean removeMapping(Object key, Object value) {
		if (!isValidKey(key))
			return false;
		if (get(key) == value) {
			int index = ((Enum<?>)key).ordinal();
			setValue(index, 0);
			//setSize(getSize() - 1);
			return true;
		}
		return false;
	}

	/**
	 * Returns true if key is of the proper type to be a key in this
	 * enum map.
	 */
	private boolean isValidKey(Object key) {
		if (key == null)
			return false;

		// Cheaper than instanceof Enum followed by getDeclaringClass
		Class<?> keyClass = key.getClass();
		return keyClass == getKeyType() || keyClass.getSuperclass() == getKeyType();
	}

	// Bulk Operations

	/**
	 * Copies all of the mappings from the specified map to this map.
	 * These mappings will replace any mappings that this map had for
	 * any of the keys currently in the specified map.
	 *
	 * @param m the mappings to be stored in this map
	 * @throws NullPointerException the specified map is null, or if
	 *     one or more keys in the specified map are null
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new RuntimeException("Unimplemented");
		/*
		if (m instanceof CustomEnumToEnumMap) {
			CustomEnumToEnumMap<?, ?> em = (CustomEnumToEnumMap<?, ?>)m;
			if (em.getKeyType() != getKeyType()) {
				if (em.isEmpty())
					return;
				throw new ClassCastException(em.getKeyType() + " != " + getKeyType());
			}

			for (int i = 0; i < keyUniverse.length; i++) {
				Object emValue = em.vals[i];
				if (emValue != null) {
					if (vals[i] == null)
						size++;
					vals[i] = emValue;
				}
			}
		} else {
			super.putAll(m);
		}*/
	}

	/**
	 * Removes all mappings from this map.
	 */
	@Override
	public final void clear() {
		enter();
		try {
			values1 = 0;
		} finally {
			leave();
		}
		//values2 = 0;
		//setSize(0);
	}

	// Views

	/**
	 * This field is initialized to contain an instance of the entry set
	 * view the first time this view is requested.  The view is stateless,
	 * so there's no reason to create more than one.
	 */
	//private transient Set<Map.Entry<K,V>> entrySet;

	/**
	 * Returns a {@link Set} view of the keys contained in this map.
	 * The returned set obeys the general contract outlined in
	 * {@link Map#keySet()}.  The set's iterator will return the keys
	 * in their natural order (the order in which the enum constants
	 * are declared).
	 *
	 * @return a set view of the keys contained in this enum map
	 */
	@Override
	public Set<K> keySet() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns a {@link Collection} view of the values contained in this map.
	 * The returned collection obeys the general contract outlined in
	 * {@link Map#values()}.  The collection's iterator will return the
	 * values in the order their corresponding keys appear in map,
	 * which is their natural order (the order in which the enum constants
	 * are declared).
	 *
	 * @return a collection view of the values contained in this map
	 */
	@Override
	public @NonNull Collection<V> values() {
		throw new UnsupportedOperationException();
		/*
		Collection<V> vs = values;
		if (vs == null) {
			vs = new Values();
			values = vs;
		}
		return vs;*/
	}

	@SuppressWarnings("unused")
	private class Values extends AbstractCollection<V> {
		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}
		@Override
		public int size() {
			return getSize();
		}
		@Override
		public boolean contains(Object o) {
			return containsValue(o);
		}
		@Override
		public boolean remove(Object o) {

			for (int i = 0; i < getKeyUniverse().length; i++) {
				int valueIndex = getValueAtIndex(i);
				if (valueIndex != 0) {
					setValue(i, 0);
					//setSize(getSize() - 1);
					return true;
				}
			}
			return false;
		}
		@Override
		public void clear() {
			CustomEnumToEnumMap.this.clear();
		}
	}

	/**
	 * Returns a {@link Set} view of the mappings contained in this map.
	 * The returned set obeys the general contract outlined in
	 * {@link Map#keySet()}.  The set's iterator will return the
	 * mappings in the order their keys appear in map, which is their
	 * natural order (the order in which the enum constants are declared).
	 *
	 * @return a set view of the mappings contained in this enum map
	 */
	@Override
	public Set<Map.Entry<K,V>> entrySet() {
		return new EntrySet();
	}

	private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
		@Override
		public Iterator<Map.Entry<K,V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof Map.Entry))
				return false;
			Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
			return containsMapping(entry.getKey(), entry.getValue());
		}
		@Override
		public boolean remove(Object o) {
			if (!(o instanceof Map.Entry))
				return false;
			Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
			return removeMapping(entry.getKey(), entry.getValue());
		}
		@Override
		public int size() {
			return getSize();
		}
		@Override
		public void clear() {
			CustomEnumToEnumMap.this.clear();
		}
		@Override
		public Object[] toArray() {
			return fillEntryArray(new Object[getSize()]);
		}
		@Override
		@SuppressWarnings("unchecked")
		public <T> T[] toArray(T[] a) {
			//@SuppressWarnings("hiding")
			int size = size();
			if (a.length < size)
				a = (T[])java.lang.reflect.Array
				.newInstance(a.getClass().getComponentType(), size);
			if (a.length > size)
				a[size] = null;
			return (T[]) fillEntryArray(a);
		}
		private Object[] fillEntryArray(Object[] a) {
			int j = 0;
			for (int i = 0; i < getKeyUniverse().length; i++) {
				V value = getValue(i);
				if (value != null)
					a[j++] = new LocalAbstractMap.SimpleEntry<>(
						getKeyUniverse()[i], value);
			}
			return a;
		}
	}

	private abstract class EnumMapIterator<T> implements Iterator<T> {
		// Lower bound on index of next element to return
		int index = 0;

		// Index of last returned element, or -1 if none
		int lastReturnedIndex = -1;

		@Override
		public boolean hasNext() {
			while (index < getKeyUniverse().length && getValueAtIndex(index) == 0)
				index++;
			return index != getKeyUniverse().length;
		}

		@Override
		public void remove() {
			checkLastReturnedIndex();

			if (getValueAtIndex(lastReturnedIndex) != 0) {
				setValue(lastReturnedIndex, 0);
				//setSize(getSize() - 1);
			}
			lastReturnedIndex = -1;
		}

		private void checkLastReturnedIndex() {
			if (lastReturnedIndex < 0)
				throw new IllegalStateException();
		}
	}

	private class ValueIterator extends EnumMapIterator<V> {
		@Override
		public V next() {
			if (!hasNext())
				throw new NoSuchElementException();
			lastReturnedIndex = index++;
			return getValue(lastReturnedIndex);
		}
	}

	private class EntryIterator extends EnumMapIterator<Map.Entry<K,V>> {
		private Entry lastReturnedEntry;

		@Override
		public Map.Entry<K,V> next() {
			if (!hasNext())
				throw new NoSuchElementException();
			lastReturnedEntry = new Entry(index++);
			return lastReturnedEntry;
		}

		@Override
		public void remove() {
			lastReturnedIndex =
				((null == lastReturnedEntry) ? -1 : lastReturnedEntry.index);
			super.remove();
			lastReturnedEntry.index = lastReturnedIndex;
			lastReturnedEntry = null;
		}

		private class Entry implements Map.Entry<K,V> {
			@SuppressWarnings("hiding")
			private int index;

			private Entry(int index) {
				this.index = index;
			}

			@Override
			public K getKey() {
				checkIndexForEntryUse();
				return getKeyUniverse()[index];
			}

			@Override
			public V getValue() {
				checkIndexForEntryUse();
				return CustomEnumToEnumMap.this.getValue(index);
			}

			@Override
			public V setValue(V value) {
				checkIndexForEntryUse();
				V oldValue = CustomEnumToEnumMap.this.getValue(index);
				setValueAtIndex(index, value);
				return oldValue;
			}

			@Override
			public boolean equals(Object o) {
				if (index < 0)
					return o == this;

				if (!(o instanceof Map.Entry))
					return false;

				Map.Entry<?,?> e = (Map.Entry<?,?>)o;
				V ourValue = CustomEnumToEnumMap.this.getValue(index);
				Object hisValue = e.getValue();
				return (e.getKey() == getKeyUniverse()[index] &&
					(ourValue == hisValue ||
					(ourValue != null && ourValue.equals(hisValue))));
			}

			@Override
			public int hashCode() {
				if (index < 0)
					return super.hashCode();

				return entryHashCode(index);
			}

			@Override
			public String toString() {
				if (index < 0)
					return super.toString();

				return getKeyUniverse()[index] + "="
					+ CustomEnumToEnumMap.this.getValue(index);
			}

			private void checkIndexForEntryUse() {
				if (index < 0)
					throw new IllegalStateException("Entry was removed");
			}
		}
	}

	// Comparison and hashing

	/**
	 * Compares the specified object with this map for equality.  Returns
	 * <tt>true</tt> if the given object is also a map and the two maps
	 * represent the same mappings, as specified in the {@link
	 * Map#equals(Object)} contract.
	 *
	 * @param o the object to be compared for equality with this map
	 * @return <tt>true</tt> if the specified object is equal to this map
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof CustomEnumToEnumMap)
			return customEquals((CustomEnumToEnumMap<?,?>)o);
		if (!(o instanceof Map))
			return false;

		Map<?,?> m = (Map<?,?>)o;
		if (getSize() != m.size())
			return false;

		for (int i = 0; i < getKeyUniverse().length; i++) {
			V value = getValue(i);
			K key = getKeyUniverse()[i];

			if (m.get(key) != value) {
				return false;
			}

		}

		return true;
	}

	private boolean customEquals(CustomEnumToEnumMap<?,?> em) {
		if (em.getKeyType() != getKeyType())
			return getSize() == 0 && em.getSize() == 0;

		return em.values1 == values1;//&& em.values2 == values2;
	}

	/**
	 * Returns the hash code value for this map.  The hash code of a map is
	 * defined to be the sum of the hash codes of each entry in the map.
	 */
	@Override
	public int hashCode() {
		int h = 0;

		for (int i = 0; i < getKeyUniverse().length; i++) {
			if (getValueAtIndex(i) != 0) {
				h += entryHashCode(i);
			}
		}

		return h;
	}

	private int entryHashCode(int index) {
		return (getKeyUniverse()[index].hashCode() ^ getValue(index).hashCode());
	}

	/**
	 * Returns a shallow copy of this enum map.  (The values themselves
	 * are not cloned.
	 *
	 * @return a shallow copy of this enum map
	 */
	@Override
	@SuppressWarnings("unchecked")
	public CustomEnumToEnumMap<K, V> clone() {
		CustomEnumToEnumMap<K, V> result;
		try {
			result = (CustomEnumToEnumMap<K, V>) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new AssertionError();
		}
		result.values1 = values1;
		//result.values2 = values2;
		//result.entrySet = null;
		return result;
	}

	/**
	 * Throws an exception if key is not of the correct type for this enum set.
	 */
	private void typeCheck(K key) {
		@SuppressWarnings("GetClassOnEnum")
		Class<?> keyClass = key.getClass();
		if (keyClass != getKeyType() && keyClass.getSuperclass() != getKeyType())
			throw new ClassCastException(keyClass + " != " + getKeyType());
	}

	/**
	 * Returns all of the values comprising K or V.
	 * The result is uncloned, cached, and shared by all callers.
	 */
	public final static <K extends Enum<K>> K[] getUniverse(Class<K> keyOrValueType) {
		return SharedSecrets.getJavaLangAccess()
			.getEnumConstantsShared(keyOrValueType);
	}

	private static final long serialVersionUID = 458661240069192865L;

	/**
	 * Save the state of the <tt>EnumMap</tt> instance to a stream (i.e.,
	 * serialize it).
	 *
	 * @serialData The <i>size</i> of the enum map (the number of key-value
	 *             mappings) is emitted (int), followed by the key (Object)
	 *             and value (Object) for each key-value mapping represented
	 *             by the enum map.
	 */
	private void writeObject(java.io.ObjectOutputStream s)
		throws java.io.IOException
	{
		// Write out the key type and any hidden stuff
		s.defaultWriteObject();

		// Write out size (number of Mappings)
		s.writeInt(getSize());

		// Write out keys and values (alternating)
		int entriesToBeWritten = getSize();
		for (int i = 0; entriesToBeWritten > 0; i++) {
			V v = getValue(i);
			if (null != v) {
				s.writeObject(getKeyUniverse()[i]);
				s.writeObject(unmaskNull(v));
				entriesToBeWritten--;
			}
		}
	}

	/**
	 * Reconstitute the <tt>EnumMap</tt> instance from a stream (i.e.,
	 * deserialize it).
	 */
	@SuppressWarnings("unchecked")
	private void readObject(java.io.ObjectInputStream s)
		throws java.io.IOException, ClassNotFoundException
	{
		// Read in the key type and any hidden stuff
		s.defaultReadObject();

		setKeyUniverse(getUniverse(getKeyType()));
		setValueUniverse(getUniverse(getValueType()));
		values1 = 0;
		//values2 = 0;

		// Read in size (number of Mappings)
		//@SuppressWarnings("hiding")
		int size = s.readInt();

		// Read the keys and values, and put the mappings in the HashMap
		for (int i = 0; i < size; i++) {
			K key = (K) s.readObject();
			V value = (V) s.readObject();
			put(key, value);
		}
	}

	@Override
	public final boolean isEmpty() {
		return getSize() == 0;
	}

	private int getSize() {
		int size = 0;
		for (int i = 0; i < getKeyUniverse().length; i++) {
			int valueIndex = getValueAtIndex(i) - 1;
			if (valueIndex >= 0) {
				size++;
			}
		}
		return size;
	}

	protected abstract Class<K> getKeyType();
	protected abstract Class<V> getValueType();
	protected abstract void setValueType(Class<V> valueType);
	protected abstract void setKeyType(Class<K> keyType);

	protected abstract K[] getKeyUniverse();

	protected abstract void setKeyUniverse(K[] keyUniverse);

	protected abstract V[] getValueUniverse();

	protected abstract void setValueUniverse(V[] valueUniverse);

	@Override
	public String toString() {
		return entrySet().toString();
	}

}
