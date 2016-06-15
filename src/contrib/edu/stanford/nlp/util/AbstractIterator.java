package contrib.edu.stanford.nlp.util;

import java.util.Iterator;

/**
 * Iterator with <code>remove()</code> defined to throw an
 * <code>UnsupportedOperationException</code>.
 */
abstract public class AbstractIterator<E> implements Iterator<E> {

  @Override
	abstract public boolean hasNext();

  @Override
	abstract public E next();

  /**
   * Throws an <code>UnupportedOperationException</code>.
   */
  @Override
	public void remove() {
    throw new UnsupportedOperationException();
  }

}
