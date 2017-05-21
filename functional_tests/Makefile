OUTPUT_COVERAGE_PROTO = 
#OUTPUT_COVERAGE_PROTO = -outputCoverageProto
MAX_MEM_OPT = -Xmx200M
JVM_BUG_WORKAROUND = 
#JVM_BUG_WORKAROUND = -XX:CompileCommand=exclude,gnu.trove.impl.hash.TObjectHash::insertKey
#https://bugs.openjdk.java.net/browse/JDK-8132870
ifndef BASEDIR
export BASEDIR := $(shell echo `pwd`)
endif
JACOCO := -javaagent:jacoco.jar=destfile=$(BASEDIR)/../target/jacoco.exec
JVM_OPTIONS = -XX:-OmitStackTraceInFastThrow -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+UseParallelGC -XX:ParallelGCThreads=3 -XX:CICompilerCount=3
MUTINACK_OPTIONS = -outputToDatabaseURL \"\" -outputSerializedTo test.out.bin
#MUTINACK_OPTIONS = -outputToDatabaseURL "jdbc:postgresql://localhost/mutinack_test_db"
nailgun_port ?= 2113
SUBMIT_TO_SERVER = -submitToServer localhost/mutinackTesting$(nailgun_port)
JAVA_LAUNCH := $(BASEDIR)/ng --nailgun-port $(nailgun_port) uk.org.cinquin.mutinack.Mutinack
#JAVA_LAUNCH := java -jar mutinack.jar

GIT := false
#Uncomment the line below to allow failed functional tests to be marked in the git repository and
#prioritized for re-running upon next call of run_functional_tests (failed tests have a FAILED
#file in their root directory, which contains the version of the program that was last used to
#run them). Git repository must be set up, with password-less authentication for pushes.
#GIT := git

#all: passed_message outputAlignments
#Uncomment the line above to have functional tests always save annotated reads in bam file for later review.
all: passed_message

DEBUG_ATTACH =  
#Uncomment the line below to remotely debug
#DEBUG_ATTACH = $(shell echo -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:`python -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()'`)

ifndef COSTLY_ASSERTIONS
export COSTLY_ASSERTIONS_OPTION = -enableCostlyAssertions true
else
export COSTLY_ASSERTIONS_OPTION = -enableCostlyAssertions $(COSTLY_ASSERTIONS)
endif

#####Progress reporter adapted from http://stackoverflow.com/questions/451413/make-makefile-progress-indication
#The progress reporter counts the number of XXX/run.out targets that will be built with the current make
#invocation. This is still a bit buggy, but only the progress report is affected; actual tests are fine.
# BUILD is initially undefined
ifndef BUILD

# max equals 256 x's
twenty := x x x x x x x x x x x x x x x x x x x x
MAX := $(foreach x,$(twenty),$(twenty))

# T estimates how many targets we are building by replacing BUILD with a special string
T := $(shell $(MAKE) -nrRf $(firstword $(MAKEFILE_LIST)) $(MAKECMDGOALS) \
            BUILD="COUNTTHIS" 2> /dev/null | grep run.out | grep -c "COUNTTHIS")

# N is the number of pending targets in base 1, well in fact, base x :-)
N := $(wordlist 1,$T,$(MAX))

# auto-decrementing counter that returns the number of pending targets in base 10
counter = $(words $N)$(eval N := $(wordlist 2,$(words $N),$N))

# BUILD is now defined to show the progress, this also avoids redefining T in loop
BUILD = $(shell expr 1 + $(T) - $(counter)) of $(T)
endif
#####End progress reporter

#Directories in which tests are to be run are identified by the presence of an
#"args.txt" file. The output file that marks tests whose output (saved to run.out) has
#been verified as correct is run.out.check.
ifndef FILES
export FILES := $(shell find . -name args.txt -print | grep -v .AppleDouble)
N_FILES := $(words $(FILES))
RESULTS := $(FILES:args.txt=run.out)
OUTPUT_ALIGNMENTS := $(FILES:args.txt=labeled_reads.bam) $(FILES:args.txt=labeled_reads.bai) $(FILES:args.txt=labeled_reads.bam.bai)
RESULT_CHECKS := $(FILES:args.txt=run.out.check)
FAILED_TESTS := $(shell find . -name FAILED -print)
FAILED_RESULTS := $(FAILED_TESTS:FAILED=run.out)
FAILED_CHECKS := $(FAILED_TESTS:FAILED=run.out.check)
endif

.PHONY: passed_message
passed_message: tests
	@echo "ALL" $(N_FILES) "TESTS PASSED (output alignments saved)"

.PHONY: outputAlignments
outputAlignments:
	@$(eval OUTPUT_ARGS = "-outputAlignmentFile labeled_reads.bam")
	@echo "Outputting alignments"

.PHONY: tests
tests: $(RESULTS) $(RESULT_CHECKS) | outputAlignments

.PHONY: results
results: $(RESULTS) | outputAlignments

.PHONY: all_results
all_results: results

.PHONY: all_checks
all_checks: $(RESULT_CHECKS)

.PHONY: all_failed_checks
all_failed_checks: $(FAILED_CHECKS)

.PHONY: all_failed_results
all_failed_results: $(FAILED_RESULTS)

#Used to force program to be re-run when the output check failed in a previous run.
#This mechanism is advantageous in that it makes it possible to keep the run.out
#files for inspection, instead of Make simply deleting them whenever a runtime or
#validation error is encountered.
FORCE_RERUN = 

all_failed: $(FAILED_CHECKS)
	@$(eval FORCE_RERUN = .FORCE)

#Mark output files as intermediate so that if program is updated after some tests
#passed and some failed, the ones that passed are not re-run. (Otherwise the
#output files would be re-created since they would be older than the updated
#program.)
#Note that this mechanism (which overlaps in functionality with the all_failed
#target) means that after an update to the program the "clean" target must
#be invoked before tests are re-run to verify that all tests that passed still pass.
#Mark as secondary so that output files are not deleted after Make completes.
.INTERMEDIATE: $(RESULTS)
.SECONDARY: $(RESULTS)

tests_no_alignment_saving: OUTPUT_ARGS = ""
tests_no_alignment_saving: $(RESULTS) $(RESULT_CHECKS)

.PHONY: clean clean_results clean_check clean_log_and_temp_files clean_jars clean_hardlinks clean_FAILED

clean: clean_jars clean_results clean_check clean_log_and_temp_files

clean_log_and_temp_files:
	@find . -name '*.log' -delete -or -name '*.check' -delete -or -name '*.swp' -delete -or -name '*.core' -delete -or -name '*.dict' -delete -or -name '*.bwt' -delete -or -name '*.bt2' -delete -or -name '*.rbwt' -delete -or -name '*.ann' -delete -or -name '*.rsa' -delete -or -name refBWA079.fa -delete -or -name BWA_OPTIONS_FILE -delete -or -name '*.amb' -delete -or -name '*.rpac' -delete -or -name '*.pac' -delete -or -name '*.cached' -delete -or -name '*.info' -delete -or -name .DS_Store -delete -or -name 'ins_stats_*' -delete -or -name 'main_stats_*' -delete -or -name 'coverage_*.txt' -delete -or -name 'refSeq*' -delete -or -name '*.sa' -delete

clean_results:
	@find . -name '*control_mutations.bed' -delete -or -name '*control_top_bottom_disag.bed' -delete -or -name '*test_mutations.bed' -delete -or -name '*test_top_bottom_disag.bed' -delete -or -name '*_coverage.bed' -delete -or -name '*_coverage.proto' -delete -or -name '*main_stats_.bed' -delete -or -name '*main_stats.bed' -delete -or -name '*ins_stats_.bed' -delete -or -name '*ins_stats.bed' -not -name 'expected_*' -delete -or -name '*_coverage.txt' -not -name 'expected_*' -delete -or -name '*_nPosDuplex.bed' -not -name 'expected_*' -delete -or -name '*.json' -delete -or -name 'labeled_reads.bam' -delete -or -name 'labeled_reads.bam.bai' -delete -or -name 'labeled_reads.bai' -delete -or -name 'run.out' -delete -or -name '*.out.bin' -delete -or -name '*randomizeStrand=*' -delete

clean_check:
	find . -name 'run.out.check' -delete -or -name 'custom_check' -delete

clean_jars:
	find . -mindepth 2 -name '*.jar' -delete

clean_FAILED:
	find . -mindepth 2 -name 'FAILED' -delete

clean_hardlinks: clean_jars
	find . -mindepth 2 '(' -name jq -delete -or -name ref.fa -delete -or -name ref.fa.fai -delete -or -name logback.xml -delete -or -name Makefile_aux_output_check -delete -or -name grep_lines_in_file -delete ')'

#Create hardlinks to the master files present in the root functional_tests directory.
#Symbolic links lead to undesirable complications, and independent copies lead to
#undesirable I/O and disk space usage.
.PRECIOUS: %/mutinack.jar %/ref.fa %/ref.fa.fai %/ref.fa.info %/logback.xml %/jacoco.jar %/grep_lines_in_file %/Makefile_aux_output_check %/jq
%/mutinack.jar:
	@ln -f mutinack.jar $@

%/jq:
	@ln -f ../jq/jq $@

%/jacoco.jar:
	@ln -f ../lib_build_run_test/org.jacoco.agent-0.7.5.201505241946-runtime.jar $@
	@ln -f ../lib_build_run_test/datanucleus-rdbms-5.0.6-SNAPSHOT.jar $*/datanucleus-rdbms-5.0.6-SNAPSHOT.jar
	@ln -f ../lib_build_run_test/datanucleus-core-5.0.7-SNAPSHOT.jar $*/datanucleus-core-5.0.7-SNAPSHOT.jar
	@ln -f ../lib_build_run_test/datanucleus-api-jdo-5.0.6-SNAPSHOT.jar $*/datanucleus-api-jdo-5.0.6-SNAPSHOT.jar
	@ln -f ../lib_build_run_test/javax.jdo-3.2.0-m7-SNAPSHOT.jar $*/javax.jdo-3.2.0-m7-SNAPSHOT.jar
	@ln -f ../lib_build_run_test/javax.persistence-2.2.0-m2-SNAPSHOT.jar $*/javax.persistence-2.2.0-m2-SNAPSHOT.jar
	@ln -f ../lib_build_run_test/jdo-api-3.1.jar $*/jdo-api-3.1.jar

%/logback.xml:
	@ln -f logback.xml $@

%/grep_lines_in_file:
	@ln -f grep_lines_in_file $@

%/Makefile_aux_output_check:
	@ln -f Makefile_aux_output_check $@

%/ref.fa:
	@ln -f ref.fa $@

%/ref.fa.info: ref.fa.info
	@ln -f ref.fa.info $@

ref.fa.info:
	@touch ref.fa.info
	@touch -t 198001010101 ref.fa.info

%/ref.fa.fai:
	@ln -f ref.fa.fai $@

export CHRI="-stopAtPosition chrIII:1 -stopAtPosition chrII:1 -stopAtPosition chrIV:1 -stopAtPosition chrV:1 -stopAtPosition chrX:1 -stopAtPosition chrM:1"
export CHRII="-stopAtPosition chrI:1 -stopAtPosition chrIII:1 -stopAtPosition chrIV:1 -stopAtPosition chrV:1 -stopAtPosition chrX:1 -stopAtPosition chrM:1"
export CHRIII="-stopAtPosition chrI:1 -stopAtPosition chrII:1 -stopAtPosition chrIV:1 -stopAtPosition chrV:1 -stopAtPosition chrX:1 -stopAtPosition chrM:1"
export CHRIV="-stopAtPosition chrI:1 -stopAtPosition chrII:1 -stopAtPosition chrIII:1 -stopAtPosition chrV:1 -stopAtPosition chrX:1 -stopAtPosition chrM:1"
export CHRV="-stopAtPosition chrI:1 -stopAtPosition chrII:1 -stopAtPosition chrIV:1 -stopAtPosition chrIII:1 -stopAtPosition chrX:1 -stopAtPosition chrM:1"
export CHRX="-stopAtPosition chrI:1 -stopAtPosition chrII:1 -stopAtPosition chrIV:1 -stopAtPosition chrV:1 -stopAtPosition chrIII:1 -stopAtPosition chrM:1"
export CHRM="-stopAtPosition chrI:1 -stopAtPosition chrII:1 -stopAtPosition chrIV:1 -stopAtPosition chrV:1 -stopAtPosition chrX:1 -stopAtPosition chrIII:1"

#TODO: introduce optional dependency on %/alignment-2/test1.bwa_mem_unpaired_no_adapt.map.sorted.bam
.PRECIOUS: %.out
%/run.out: %/mutinack.jar %/jacoco.jar %/ref.fa %/ref.fa.info %/ref.fa.fai %/logback.xml %/args.txt %/test_genome_locations.txt %/alignment-1/test1.bwa_mem_unpaired_no_adapt.map.sorted.bam $(FORCE_RERUN) 
	@echo _____Computing $(BUILD): $@
#collapseFilteredReads
	@(cd $* && $(JAVA_LAUNCH) $(COSTLY_ASSERTIONS_OPTION) $(MUTINACK_OPTIONS) $(shell test -e $*/no_jiggle || echo -jiggle) -noStatusMessages -runName $(BASEDIR)/"$*" -referenceOutput $(BASEDIR)/$*/expected_output.txt -auxOutputFileBaseName $(BASEDIR)/"$*"/ -workingDirectory $(BASEDIR)/"$*" $(SUBMIT_TO_SERVER) $(OUTPUT_COVERAGE_PROTO) -readContigsFromFile -verbosity 3 -referenceGenome ref.fa $(shell echo $(OUTPUT_ARGS)) -outputJSONTo $(BASEDIR)/"$*"/stats.json -sortOutputAlignmentFile true -logReadIssuesInOutputBam true -forceOutputAtPositionsFile test_genome_locations.txt $(shell env CHRI=$(CHRI) CHRII=$(CHRII) CHRIII=$(CHRIII) CHRIV=$(CHRIV) CHRV=$(CHRV) CHRX=$(CHRX) CHRM=$(CHRM) sh -c 'cat $*/args.txt | envsubst')) > $@ && export res=0 || export res=1; test -e $*/expect_error && export expect_error=0 || export expect_error=1; eval 'test 1 -eq $$(($$res ^ $$expect_error))' || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false )

.PRECIOUS: %.out
%/run.out: %/mutinack.jar %/jacoco.jar %/ref.fa %/ref.fa.info %/ref.fa.fai %/logback.xml %/args.txt %/test_genome_locations.txt %/alignment-1/alignment-1.bam $(FORCE_RERUN)
	@echo _____Computing $(BUILD): $@
	@(cd $* && $(JAVA_LAUNCH) $(COSTLY_ASSERTIONS_OPTION) $(MUTINACK_OPTIONS) $(shell test -e $*/no_jiggle || echo -jiggle) -noStatusMessages -runName $(BASEDIR)/"$*" -referenceOutput $(BASEDIR)/$*/expected_output.txt -auxOutputFileBaseName $(BASEDIR)/"$*"/ -workingDirectory $(BASEDIR)/"$*" $(SUBMIT_TO_SERVER) $(OUTPUT_COVERAGE_PROTO) -readContigsFromFile -verbosity 3 -referenceGenome ref.fa $(shell echo $(OUTPUT_ARGS)) -outputJSONTo $(BASEDIR)/"$*"/stats.json -sortOutputAlignmentFile true -logReadIssuesInOutputBam true -forceOutputAtPositionsFile test_genome_locations.txt $(shell env CHRI=$(CHRI) CHRII=$(CHRII) CHRIII=$(CHRIII) CHRIV=$(CHRIV) CHRV=$(CHRV) CHRX=$(CHRX) CHRM=$(CHRM) sh -c 'cat $*/args.txt | envsubst')) > $@ && export res=0 || export res=1; test -e $*/expect_error && export expect_error=0 || export expect_error=1; eval 'test 1 -eq $$(($$res ^ $$expect_error))' || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false )

AVOID_STUPID_RULE_SIDE_EFFECT = $(MAKE)

.DELETE_ON_ERROR:
%/run.out.check: %/run.out %/Makefile_aux_output_check %/grep_lines_in_file %/expected_output.txt
	@((./grep_lines_in_file $*/run.out < $*/expected_output.txt > $@) && (cd $*/ && $(AVOID_STUPID_RULE_SIDE_EFFECT) -s -j1 -f Makefile_aux_output_check 2>/dev/null)) && ($(GIT) rm -f $*/FAILED > /dev/null 2>&1 || rm -f $*/FAILED) || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false ) 

%/run.out.check: %/run.out %/Makefile_aux_output_check %/custom_check_script %/jq test_options/positive_strand/minBasePhredScoreQ2/test_Q2_score_27_criteria_25_substitution_detection/run.out test_options/positive_strand/minBasePhredScoreQ2/test_Q2_score_27_criteria_30_no_detection/run.out
	@(cd $*/ && $(AVOID_STUPID_RULE_SIDE_EFFECT) -s -j1 -f Makefile_aux_output_check 2>/dev/null) && ($(GIT) rm -f $*/FAILED > /dev/null 2>&1 || rm -f $*/FAILED) || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false )

