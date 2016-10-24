[![Build Status](https://travis-ci.org/cinquin/mutinack.svg?branch=master)](https://travis-ci.org/cinquin/mutinack)
<!-- [![Coverity scan](https://scan.coverity.com/projects/4876/badge.svg)](https://scan.coverity.com/projects/4876) -->

**General notes**

"Mutinack" is a program that detects mutations genome-wide with an error
rate estimated to be as low as ~10<sup>-10</sup>, using Illumina paired
end sequencing of minute amounts of starting material prepared following
the SIP-HAVA-Seq protocol — as described in an accompanying manuscript
\[[1](#1)\] (see \[[2](#2)\] for the original report of the duplex
sequencing idea). Mutinack can report detailed statistics on mutations
and top/bottom strand disagreements over any genome region defined in a
custom BED file. While Mutinack was primarily designed with DNA-Seq in
mind, it can accessorily be used to collapse RNA-Seq reads to only keep
one set of reads per molecule in a cDNA library, which minimizes noise
when quantifying gene expression (see e.g. \[[1](#3)-[2](#4)\]).

**Installing and building Mutinack**

Mutinack is a Java program that comes with all dependencies pre-packaged
in a single `mutinack.jar` file. The only runtime requirement is a Java
\>= 8 runtime (and GNU make and Perl with module File::Grep for
functional tests), and Mutinack should thus run on any platform that has
such a runtime (FreeBSD, Mac OS X, and Linux have been tested with
OpenJDK 1.8 or 1.9 early access build 114). The `mutinack.jar` file can
be [directly downloaded](http://cinquin.org.uk/static/mutinack.jar), or
built from a clone of this repository using `ant jar` (Ant is
technically not required but makes the build very straightforward; Git
is used to optionally include version information in the build). Be sure
to perform a recursive clone of this repository (passing Git the
`--recursive` argument). Note that on startup Mutinack checks whether a
newer version is available for download; this can be disabled with the
`-skipVersionCheck` flag.

**Tests**

Unit tests as well as a number of functional tests are provided. The
former are available as Ant target `junitreport`, and the latter can be
run using the `run_functional_tests` script in the base directory. Both
sets of tests are run automatically as part of continuous integration
builds. Each of the ~350 functional tests takes a relatively large
amount of time to complete because of initialization costs, but the
tests are run in parallel; the number of simultaneous jobs should be
adjusted to match machine architecture using the `N_PARALLEL_JOBS` knob
in the `run_functional_tests` script. Functional tests further rely on
Nailgun (provided as a Git submodule) to avoid JVM startup costs and to
optimize speed by using "warm" JVM instances; Nailgun needs to have been
built for the tests to run. The functional test setup includes a
mechanism to mark failed tests and automatically track them in a Git
repository, which makes it possible to prioritize re-running of these
tests when performing continuous integration builds across multiple
machines (with e.g. Jenkins); this functionality is turned off by
default to minimize initial configuration effort. Functional tests
record code coverage using JaCoCo (use Ant target `jacoco:report` to
produce a report).

The project is set up for [mutation
testing](https://en.wikipedia.org/wiki/Mutation_testing) (these are
mutations in code, not to be confused with mutations in DNA) using a
[customized version](https://github.com/cinquin/pitest) of
[PIT](http://pitest.org). This analysis identifies parts of the code
that are sufficiently well constrained and covered by tests for changes
introduced as code "mutations" to cause functional or unit tests to
fail. Since the analysis is computationally costly, pre-computed
mutation testing results have been made available
[here](http://cinquin.org.uk/static/mutation_testing/) in HTML format.
Note that as it is the analysis produces a number of false positives
(i.e. mutations that that are not caught by tests, highlighted in red in
the HTML output) for various reasons (e.g. because the mutated code
improved performance without changing Mutinack's output, because it
implemented internal sanity checks that also did not affect Mutinack's
output, or because there is more than one way to achieve the exact same
result). Note also that for now the tests do not aim to be exhaustive;
they focus on the critical parts of the algorithm and of the output,
rather than on all the functionality that is useful but that is not high
priority to specifically test. The analysis can be run with `ant
mutationCoverage` (run time is up to 1 day on a 64-core machine,
depending on the mutators used).

The project is compiled with Google's "Error Prone" static analyzer, and
comes with Ant and Maven targets to run Findbugs, which is pre-packaged
in the distribution. The code is also regularly analyzed with Coverity
Scan, which has very nice additions to Findbugs and does not currently
detect any significant issue. Note however that recent versions of
Mutinack cannot be analyzed with Coverity, because of an apparent bug in
Coverity that has been reported to Synopsys. The Findbugs analysis
requires a custom Findbugs version (packaged with Mutinack) that
contains a version of the ASM bytecode engineering library that we
modified to work around a bug in the Java compiler documented
[here](https://github.com/cinquin/javac_bug) and that Oracle has
acknowledged (but not yet fixed) as [OpenJDK bug
8144185](https://bugs.openjdk.java.net/browse/JDK-8144185).

Note also that, at least on some versions of JRE 8, Mutinack can trigger
a non-deterministic Java Virtual Machine bug, which was
[reported](https://bugs.openjdk.java.net/browse/JDK-8132870) to Oracle
and [appears to have been
resolved](https://bugs.openjdk.java.net/browse/JDK-8150446) from JRE
8u74 onwards. This bug has only been observed in functional tests; to
work around it, use the following JVM flags:
`-XX:CompileCommand=exclude,gnu.trove.impl.hash.TObjectHash::insertKey`
and `-XX:CompileCommand=exclude,gnu.trove.impl.hash.TIntHash::insertKey`.

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
patterns: `-XX:+UseParallelGC -XX:ParallelGCThreads=10 -Xmx15G`. With
these settings we can achieve a throughput in excess of 3.5 million
bases / s on our hardware (processing speed is data dependent).

Performance can be increased by turning off costly internal assertions
that provide internal sanity checks, using `-enableCostlyAssertions false`; 
assertions that have a minimal performance impact can only be turned off
by editing the code.

Under FreeBSD or Mac OS X, press control-T (or `kill -INFO` the process)
to see a progress update on the standard error stream.

**Distributed computing**

Mutinack can use Java's Remote Method Invocation to run server and
worker processes distributed over multiple JVM instances and multiple
hosts, in a fault-tolerant fashion. See `-submitToServer`,
`-startWorker` and `-startServer` options. Connections between different
processes are secured and two-way authenticated using SSL. To ensure
that the authentication cannot be trivially bypassed by would-be
attackers (which may allow any user to access all files readable by the
server process), be sure to run the included `regenerate_SSL_files`
script after cloning the repository, or set up your own certificates and
keys in the trust store and key store.

**Contributing**

Any contributions under the form of suggestions, bug reports, bug fixes,
or new functionality will be greatly appreciated. Please create a Github
issue and/or submit code as a pull request (the continuous integration
system should automatically check the pull request).

**Credits**

Mutinack relies on the following libraries:

- [Picard tools](http://sourceforge.net/projects/picard/) by the Broad
Institute (MIT License, Apache License V2.0) and its bzip2 and snappy
dependencies: used to read BAM files. Some changes were made to improve
performance (memoization of methods highlighted by profiling).

- [JCommander](http://jcommander.org) by Cédric Beust (Apache License
V2.0): used to parse command-line arguments

- [GNU Trove](http://trove4j.sourceforge.net/html/overview.html) (GNU
Lesser General Public License): used for its implementations of Maps and
Sets that minimize object creation, and for Lists of primitives. Small
changes made are independently released
[here](https://github.com/cinquin/GNU_Trove).

- [Furious object pool](https://code.google.com/p/furious-objectpool/)
by eddie (Apache license)

- [Jackson](http://wiki.fasterxml.com/JacksonHome) by FasterXML (Apache
License V2.0): used for JSON output

- [fast-serialization](https://github.com/RuedigerMoeller/fast-
serialization) by Ruediger Moeller (Apache License Version 2.0): used to
cache data structures that are parsed (slowly) from text files

- [Google Protobuf](https://github.com/google/protobuf/)
([license](https://github.com/google/protobuf/blob/master/LICENSE)):
used for serialization of detailed genome coverage data

- [Apache Commons IO](https://commons.apache.org/proper/commons-io/)
(Apache License Version 2.0)

- An adapted version of
[java-algorithms-implementation](https://github.com/phishman3579/java-
algorithms-implementation) by Justin Wetherell (Apache License). Some
changes were integrated upstream; others are available
[here](https://github.com/cinquin/mutinack/blob/master/src/com/
jwetherell/algorithms/data_structures/IntervalTree.java).

- [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml) by
The Stanford Natural Language Processing Group (GNU Public License v3)

- [JUnit](http://http://junit.org), [Hamcrest](https://code.google.
com/p/hamcrest/), and [JMockit](http://jmockit.org) (Eclipse Public
License, BSD license, and MIT license, respectively): used for unit
testing

- [PIT](http://pitest.org) by Henry Coles (Apache License Version 2.0):
used for mutation testing; the version included with Mutinack was
[adapted](https://github.com/cinquin/pitest) to provide more information
on test failures, among other things

- [JaCoCo](http://eclemma.org/jacoco/) (Eclipse Public License v1.0):
used for code coverage analysis

- [Findbugs](http://findbugs.sourceforge.net) (GNU Lesser General Public
License): used for static analysis

- [Error Prone](http://errorprone.info) by Google (Apache License
Version 2.0): used for static analysis at compile time

- [ASM library](http://asm.ow2.org) by the OW2 Consortium (BSD license):
used by Findbugs and JaCoCo; modified to work around [OpenJDK bug
8144185](https://bugs.openjdk.java.net/browse/JDK-8144185)

- [EqualsVerifier](http://www.jqno.nl/equalsverifier/) by Jan Ouwens
(Apache License Version 2.0): used for testing of `equals` and
`hashCode` methods

- [Logback](http://logback.qos.ch), the [SLF4J](http://www.slf4j.org)
API and the Lidalia extension for logging (LGLP, MIT, and MIT X11
licenses, respectively): used for logging

- [RMIIO](https://github.com/jahlborn/rmiio) (Apache License Version
2.0): used to stream data over RMI.
[Modified](https://github.com/cinquin/rmiio) to create a shaded jar
artifact.

- A jar file containing Eclipse Null/Nullable annotations is included
for convenience (Eclipse Public License)

Travis and Coverity kindly provide free resources for continuous
integration and static code analysis.

**License**

Mutinack is released under the GNU Affero General Public License version
3.

**References**

<a name="1"></a>\[1\]: Taylor, P.H., Cinquin, A., Cinquin, O. (2016).
Quantification of in vivo progenitor mutation accrual with ultra-low
error rate and minimal input DNA using SIP-HAVA-seq. Genome Research
[advance online
article](http://genome.cshlp.org/content/early/2016/10/19/gr.200501.115.
full.pdf+html).

<a name="2"></a>\[2\]: Schmitt, M.W., Kennedy, S.R., Salk, J.J., Fox,
E.J., Hiatt, J.B., and Loeb, L.A. (2012). Detection of ultra-rare
mutations by next-generation sequencing. Proc Natl Acad Sci U S A 109,
14508–14513.

<a name="3"></a>\[3\]: Shiroguchi, K., Jia, T.Z., Sims, P.A., Xie, X.S.
(2012). Digital RNA sequencing minimizes sequence-dependent bias and
amplification noise with optimized single-molecule barcodes. Proc Natl
Acad Sci U S A 109, 1347–1352.

<a name="4"></a>\[4\]: Fu, G.K., Xu, W., Wilhelmy, J., Mindrinos, M.N.,
Davis, R.W., Xiao, W., Fodor, S.P.A. (2014). Molecular indexing enables
quantitative targeted RNA sequencing and reveals poor efficiencies in
standard library preparations. Proc Natl Acad Sci U S A 111, 1891–1896.
