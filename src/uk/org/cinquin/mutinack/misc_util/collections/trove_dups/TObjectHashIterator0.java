package uk.org.cinquin.mutinack.misc_util.collections.trove_dups;

import gnu.trove.impl.hash.TObjectHash;


/**
 * Iterator for hashtables that use open addressing to resolve collisions.
 *
 * @author Eric D. Friedman
 * @author Rob Eden
 * @author Jeff Randall
 * @version $Id: TObjectHashIterator.java,v 1.1.2.4 2009/10/09 01:44:34 robeden Exp $
 */

public class TObjectHashIterator0<E, V> extends THashIterator0<E, V> {

    protected final TObjectHash0<E> _objectHash;


    public TObjectHashIterator0( TObjectHash0<E> hash ) {
        super( hash );
        _objectHash = hash;
    }


    @Override
		@SuppressWarnings("unchecked")
    protected V objectAtIndex( int index ) {
        Object obj = _objectHash._set[index];
        if ( obj == TObjectHash.FREE || obj == TObjectHash.REMOVED ) {
            return null;
        }
        return (V) obj;
    }

} // TObjectHashIterator
