package uk.org.cinquin.mutinack.misc_util.collections.trove_dups;


import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import gnu.trove.iterator.TIterator;



/**
 * Implements all iterator functions for the hashed object set.
 * Subclasses may override objectAtIndex to vary the object
 * returned by calls to next() (e.g. for values, and Map.Entry
 * objects).
 * <p/>
 * <p> Note that iteration is fastest if you forego the calls to
 * <tt>hasNext</tt> in favor of checking the size of the structure
 * yourself and then call next() that many times:
 * <p/>
 * <pre>
 * Iterator i = collection.iterator();
 * for (int size = collection.size(); size-- > 0;) {
 *   Object o = i.next();
 * }
 * </pre>
 * <p/>
 * <p>You may, of course, use the hasNext(), next() idiom too if
 * you aren't in a performance critical spot.</p>
 */
public abstract class THashIterator0<E, V> implements TIterator, Iterator<V> {


    private final TObjectHash0<E> _object_hash;

    /** the data structure this iterator traverses */
    protected final THash0 _hash;

    /**
     * the number of elements this iterator believes are in the
     * data structure it accesses.
     */
    protected int _expectedSize;

    /** the index used for iteration. */
    protected int _index;


    /**
     * Create an instance of THashIterator over the values of the TObjectHash
     *
     * @param hash the object
     */
    protected THashIterator0( TObjectHash0<E> hash ) {
        _hash = hash;
        _expectedSize = _hash.size();
        _index = _hash.capacity();
        _object_hash = hash;
    }


    /**
     * Moves the iterator to the next Object and returns it.
     *
     * @return an <code>Object</code> value
     * @throws ConcurrentModificationException
     *                                if the structure
     *                                was changed using a method that isn't on this iterator.
     * @throws NoSuchElementException if this is called on an
     *                                exhausted iterator.
     */
    @Override
		public V next() {
        moveToNextIndex();
        return objectAtIndex( _index );
    }


    /**
     * Returns true if the iterator can be advanced past its current
     * location.
     *
     * @return a <code>boolean</code> value
     */
    @Override
		public boolean hasNext() {
        return nextIndex() >= 0;
    }


    /**
     * Removes the last entry returned by the iterator.
     * Invoking this method more than once for a single entry
     * will leave the underlying data structure in a confused
     * state.
     */
    @Override
		public void remove() {
        if ( _expectedSize != _hash.size() ) {
            throw new ConcurrentModificationException();
        }

        // Disable auto compaction during the remove. This is a workaround for bug 1642768.
        try {
            _hash.tempDisableAutoCompaction();
            _hash.removeAt( _index );
        }
        finally {
            _hash.reenableAutoCompaction( false );
        }

        _expectedSize--;
    }


    /**
     * Sets the internal <tt>index</tt> so that the `next' object
     * can be returned.
     */
    protected final void moveToNextIndex() {
        // doing the assignment && < 0 in one line shaves
        // 3 opcodes...
        if ( ( _index = nextIndex() ) < 0 ) {
            throw new NoSuchElementException();
        }
    }


    /**
     * Returns the index of the next value in the data structure
     * or a negative value if the iterator is exhausted.
     *
     * @return an <code>int</code> value
     * @throws ConcurrentModificationException
     *          if the underlying
     *          collection's size has been modified since the iterator was
     *          created.
     */
    protected final int nextIndex() {
        if ( _expectedSize != _hash.size() ) {
            throw new ConcurrentModificationException();
        }

        Object[] set = _object_hash._set;
        int i = _index;
        while ( i-- > 0 && ( set[i] == TObjectHash0.FREE || set[i] == TObjectHash0.REMOVED ) ) {
            ;
        }
        return i;
    }


    /**
     * Returns the object at the specified index.  Subclasses should
     * implement this to return the appropriate object for the given
     * index.
     *
     * @param index the index of the value to return.
     * @return an <code>Object</code> value
     */
    abstract protected V objectAtIndex( int index );
} // THashIterator