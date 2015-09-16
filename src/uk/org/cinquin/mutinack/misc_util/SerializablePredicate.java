package uk.org.cinquin.mutinack.misc_util;

import java.io.Serializable;
import java.util.function.Predicate;

public interface SerializablePredicate<T> extends Predicate<T>, Serializable {

}
