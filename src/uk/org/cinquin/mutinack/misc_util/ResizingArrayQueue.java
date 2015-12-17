package uk.org.cinquin.mutinack.misc_util;

import java.util.Collection;

/******************************************************************************
 *  Compilation:  javac ResizingArrayQueue.java
 *  Execution:    java ResizingArrayQueue < input.txt
 *  Dependencies: StdIn.java StdOut.java
 *  Data files:   http://algs4.cs.princeton.edu/13stacks/tobe.txt  
 *  
 *  Queue implementation with a resizing array.
 *
 *  % java ResizingArrayQueue < tobe.txt 
 *  to be or not to be (2 left on queue)
 *
 ******************************************************************************/

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 *  The <tt>ResizingArrayQueue</tt> class represents a first-in-first-out (FIFO)
 *  queue of generic items.
 *  It supports the usual <em>enqueue</em> and <em>dequeue</em>
 *  operations, along with methods for peeking at the first item,
 *  testing if the queue is empty, and iterating through
 *  the items in FIFO order.
 *  <p>
 *  This implementation uses a resizing array, which double the underlying array
 *  when it is full and halves the underlying array when it is one-quarter full.
 *  The <em>enqueue</em> and <em>dequeue</em> operations take constant amortized time.
 *  The <em>size</em>, <em>peek</em>, and <em>is-empty</em> operations takes
 *  constant time in the worst case. 
 *  <p>
 *  For additional documentation, see <a href="http://algs4.cs.princeton.edu/13stacks">Section 1.3</a> of
 *  <i>Algorithms, 4th Edition</i> by Robert Sedgewick and Kevin Wayne.
 *
 *  @author Robert Sedgewick
 *  @author Kevin Wayne
 */
public class ResizingArrayQueue<Item> implements Iterable<Item>, Queue<Item> {
    private Item[] q;       // queue elements
    private int N;          // number of elements on queue
    private int first;      // index of first element of queue
    private int last;       // index of next available slot


    /**
     * Initializes an empty queue.
     */
    @SuppressWarnings("unchecked")
	public ResizingArrayQueue(int initialSize) {
        q = (Item[]) new Object[initialSize];
        N = 0;
        first = 0;
        last = 0;
    }

    /**
     * Is this queue empty?
     * @return true if this queue is empty; false otherwise
     */
    @Override
	public boolean isEmpty() {
        return N == 0;
    }

    /**
     * Returns the number of items in this queue.
     * @return the number of items in this queue
     */
    @Override
	public int size() {
        return N;
    }

    // resize the underlying array
    private void resize(int max) {
        assert max >= N;
        @SuppressWarnings("unchecked")
		Item[] temp = (Item[]) new Object[max];
        for (int i = 0; i < N; i++) {
            temp[i] = q[(first + i) % q.length];
        }
        q = temp;
        first = 0;
        last  = N;
    }

    /**
     * Adds the item to this queue.
     * @param item the item to add
     */
    public void enqueue(Item item) {
        // double size of array if necessary and recopy to front of array
        if (N == q.length) resize(2*q.length);   // double size of array if necessary
        q[last++] = item;                        // add item
        if (last == q.length) last = 0;          // wrap-around
        N++;
    }

    /**
     * Removes and returns the item on this queue that was least recently added.
     * @return the item on this queue that was least recently added
     * @throws java.util.NoSuchElementException if this queue is empty
     */
    public Item dequeue() {
        if (isEmpty()) throw new NoSuchElementException("Queue underflow");
        Item item = q[first];
        q[first] = null;                            // to avoid loitering
        N--;
        first++;
        if (first == q.length) first = 0;           // wrap-around
        // shrink size of array if necessary
        //if (N > 0 && N == q.length/4) resize(q.length/2); 
        return item;
    }

    /**
     * Returns the item least recently added to this queue.
     * @return the item least recently added to this queue
     * @throws java.util.NoSuchElementException if this queue is empty
     */
    @Override
	public Item peek() {
        if (isEmpty()) throw new NoSuchElementException("Queue underflow");
        return q[first];
    }


    /**
     * Returns an iterator that iterates over the items in this queue in FIFO order.
     * @return an iterator that iterates over the items in this queue in FIFO order
     */
    @Override
	public Iterator<Item> iterator() {
        return new ArrayIterator();
    }

    // an iterator, doesn't implement remove() since it's optional
    private class ArrayIterator implements Iterator<Item> {
        private int i = 0;
        @Override
		public boolean hasNext()  { return i < N;                               }
        @Override
		public void remove()      { throw new UnsupportedOperationException();  }

        @Override
		public Item next() {
            if (!hasNext()) throw new NoSuchElementException();
            Item item = q[(i + first) % q.length];
            i++;
            return item;
        }
    }

	@Override
	public boolean contains(Object o) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public Object[] toArray() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean remove(Object o) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean addAll(Collection<? extends Item> c) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public void clear() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean add(Item e) {
		enqueue(e);
		return true;
	}

	@Override
	public boolean offer(Item e) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public Item remove() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public Item poll() {
		return dequeue();
	}

	@Override
	public Item element() {
		throw new RuntimeException("Unimplemented");
	}


}
