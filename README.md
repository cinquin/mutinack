[![Build Status](https://travis-ci.org/cinquin/mutinack.svg?branch=master)](https://travis-ci.org/cinquin/mutinack)
[![Coverity scan](https://scan.coverity.com/projects/4876/badge.svg)](https://scan.coverity.com/projects/4876)

**General notes**

"Mutinack" is a program that detects extraordinarily rare mutations 
(with a frequency as low as ~10<sup>-10</sup>) from Illumina paired end
sequencing of minute amounts of starting material, using a DNA library
preparation protocol and an algorithm described in an upcoming
manuscript. Mutinack can report detailed statistics on mutations and
top/bottom strand disagreements over any genome region defined in a
custom BED file. While Mutinack was primarily designed with DNA-Seq in
mind, it can accessorily be used to collapse RNA-Seq reads to only keep
one set of reads per molecule in a cDNA library, which minimizes noise
when quantifying gene expression (see e.g. \[1-2\]).

**Installing and building Mutinack**

Mutinack is a Java program that comes with all dependencies pre-packaged
in a single `mutinack.jar` file. The only runtime requirement is a Java
\>= 8 runtime (and GNU make and Perl with module File::Grep for
functional tests), and Mutinack should thus run on any platform that has
such a runtime (FreeBSD, Mac OS X, and Linux have been tested with
OpenJDK 1.8 or 1.9 early access b76). The `mutinack.jar` file can be
[directly downloaded](http://cinquin.org.uk/static/mutinack.jar), or
built from a clone of this repository using `ant insert-git-info
unjar_dependencies jar` (`ant` is technically not required but makes the
build very straightforward; `git` is used to optionally include version
information in the build). Note that on startup Mutinack checks whether
a newer version is available for download; this can be disabled with the
`-skipVersionCheck` flag.

**Tests**

Unit tests as well as a number of functional tests are provided. The
former are available as `ant` target `junitreport`, and the latter can
be run using the `run_functional_tests` script in the base directory.
Both sets of tests are run automatically as part of continuous
integration builds (each of the ~170 functional tests takes a relatively
large amount of time to complete because of initialization costs, but the
tests are run in parallel; the number of simultaneous jobs should be
adjusted to match machine architecture using the `N_PARALLEL_JOBS` knob
in the `run_functional_tests` script). The functional test setup includes
a mechanism to mark failed tests and automatically track them in a Git
repository, which makes it possible to prioritize re-running of these
tests when performing continuous integration builds across multiple
machines (with e.g. Jenkins); this functionality is turned off by default
to minimize initial configuration effort. Functional tests record
code coverage using JaCoCo (use `ant` target `jacoco:report` to produce a
report).

The project comes with `ant` and `maven` targets to run Findbugs, which
is pre-packaged in the distribution.

Note that, at least on some versions of JRE 8, Mutinack can trigger a
non-deterministic Java Virtual Machine bug, which has been
[reported](https://bugs.openjdk.java.net/browse/JDK-8132870) to
Oracle but apparently remains unaddressed (the original bug reported is
possibly a duplicate of [this bug](https://bugs.openjdk.java.net/browse/JDK-6675699)).
The JVM bug has only been observed in functional tests, which work around
the problem by specifying the following JVM option:
`-XX:CompileCommand=exclude,gnu.trove.impl.hash.TObjectHash::insertKey`.

**Sample datasets**

BAM files derived from alignment of *C. elegans* germ cell sequence data
will be made available
[here](http://cinquin.org.uk/static/sequence_data/).

**Running Mutinack**

Use `java -jar mutinack.jar -help` for a list of arguments and
explanations. Mandatory arguments are marked with a star; at the minimum
one must specify BAM files containing aligned sequence data (each BAM
file must have a matching index file), and an indexed FASTA reference
genome file. Alignments must be generated in a way that the custom
barcodes are removed from the raw sequence data and placed in a `BC`
attribute (with an optional `BQ` attribute containing the corresponding
base read qualities), which can be achieved using the included
`trim_reads_move_BC` Perl script. Alignments can be merged from multiple
sequencing runs, even if the runs did not have identical read lengths.
If multiple input BAM files are provided, mutations appearing in only
one of these files will be highlighted by a star in the output and will
be used to compute mutation rates.

Mutinack runs some operations in a multithreaded fashion. More
parallelism can be extracted by specifying that contigs should be broken
up into chunks to be treated independently, using the
`parallelizationFactor` parameter; the performance gain is noticeable
for well-chosen values of that parameter. Note that currently changes
in `parallelizationFactor` lead to infinitesimal changes in computed
coverage - this will be addressed in a future version.

Although efforts were made to choose algorithms and implementations that
do not create an inordinate number of short-lived objects, object
allocation and garbage collection can limit performance. We use the
following JVM arguments, that were derived mostly by empirical means and
that might need to be adapted for different architectures or usage
patterns: `-XX:ParallelGCThreads=10 -Xmx15G`. With these settings we can
achieve a throughput in excess of 3.5 million bases / s on our hardware
(processing speed is data dependent). Performance can be increased by
turning off internal assertions that provide sanity checks; these 
assertions are kept on by default because the performance gain (which is
also data dependent) is in general not substantial.

Under FreeBSD or Mac OS X, press control-T (or `kill -INFO` the process)
to see a progress update on the standard error stream.

**Contributing**

Any contributions under the form of suggestions, bug reports, bug fixes,
or new functionality will be greatly appreciated. Please create a Github
issue and/or submit code as a pull request (the continuous integration
system should automatically check the pull request).

**Credits**

Mutinack relies on the following libraries:

- [Picard tools](http://sourceforge.net/projects/picard/) by the Broad
Institute (MIT License, Apache License V2.0) and its bzip2, snappy
and sun.tools dependencies: used to read BAM files. Some changes were
made to improve performance (memoization of methods highlighted by
profiling).

- [JCommander](http://jcommander.org) by Cédric Beust: used to parse
command-line arguments (Apache License V2.0)

- [Guava](https://github.com/google/guava) by Google (Apache License
V2.0)

- [GNU Trove](http://trove4j.sourceforge.net/html/overview.html) (LGPL):
used for its implementations of Maps and Sets that minimize object
creation, and for Lists of primitives. Small changes made are
independently released [here](https://github.com/cinquin/GNU_Trove).

- [Furious object pool](https://code.google.com/p/furious-objectpool/)
by eddie (Apache license)

- An adapted version of
[java-algorithms-implementation](https://github.com/phishman3579/java-algorithms-implementation)
by Justin Wetherell (Apache License). Some changes were integrated
upstream; others are available
[here](https://github.com/cinquin/mutinack/blob/master/src/com/jwetherell/algorithms/data_structures/IntervalTree.java).

- [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml) by
The Stanford Natural Language Processing Group (GNU Public License v3)

- [JUnit](http://http://junit.org) and [Hamcrest](https://code.google.
com/p/hamcrest/) are used for unit testing (Eclipse Public License
and BSD license, respectively) and have their jars included

- [JaCoCo](http://eclemma.org/jacoco/), used for code coverage
analysis (Eclipse Public License v1.0)

- [Logback](http://logback.qos.ch), the [SLF4J](http://www.slf4j.org)
API and the Lidalia extension for logging (LGLP, MIT, and MIT X11
licenses, respectively)

- A jar file containing Eclipse Null/Nullable annotations is included
for convenience (Eclipse Public License)

**License**

Mutinack is released under the GNU Affero General Public License version
3.

**References**

\[1\]: Shiroguchi, K., Jia, T.Z., Sims, P.A., Xie, X.S. (2012). Digital
RNA sequencing minimizes sequence-dependent bias and amplification noise
with optimized single-molecule barcodes. Proc Natl Acad Sci U S A 109,
1347–1352.

\[2\]: Fu, G.K., Xu, W., Wilhelmy, J., Mindrinos, M.N., Davis, R.W.,
Xiao, W., Fodor, S.P.A. (2014). Molecular indexing enables quantitative
targeted RNA sequencing and reveals poor efficiencies in standard
library preparations. Proc Natl Acad Sci U S A 111, 1891–1896.
