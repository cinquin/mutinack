OUTPUT_COVERAGE_PROTO = 
#OUTPUT_COVERAGE_PROTO = -outputCoverageProto
MAX_MEM_OPT = -Xmx200M
JVM_BUG_WORKAROUND = 
#JVM_BUG_WORKAROUND = -XX:CompileCommand=exclude,gnu.trove.impl.hash.TObjectHash::insertKey
#https://bugs.openjdk.java.net/browse/JDK-8132870
JACOCO := $(shell echo -javaagent:jacoco.jar=destfile=`pwd`/../target/jacoco.exec)
JVM_OPTIONS = -XX:+UseParallelGC -XX:ParallelGCThreads=3 -XX:CICompilerCount=3
SUBMIT_TO_SERVER = -submitToServer localhost
PATH_TO_NG = $(shell echo `pwd`/ng)
BASEDIR = $(shell echo `pwd`)

#GIT := false
#Uncomment the line below to allow failed functional tests to be marked in the git repository and
#prioritized for re-running upon next call of run_functional_tests (failed tests have a FAILED
#file in their root directory, which contains the version of the program that was last used to
#run them). Git repository must be set up, with password-less authentication for pushes.
GIT := git

#all: passed_message outputAlignments
#Uncomment the line above to have functional tests always save annotated reads in bam file for later review.
all: passed_message

DEBUG_ATTACH =  
#Uncomment the line below to remotely debug
#DEBUG_ATTACH = -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=6006

#####Progress reporter adapted from http://stackoverflow.com/questions/451413/make-makefile-progress-indication
#The progress reporter counts the number of XXX/run.out targets that will be built with the current make
#invocation. This is still a bit buggy, but only the progress report is affected; actual tests are fine.
# BUILD is initially undefined
ifndef BUILD

# max equals 256 x's
sixteen := x x x x x x x x x x x x x x x x
MAX := $(foreach x,$(sixteen),$(sixteen))

# T estimates how many targets we are building by replacing BUILD with a special string
T := $(shell $(MAKE) -nrRf $(firstword $(MAKEFILE_LIST)) $(MAKECMDGOALS) \
            BUILD="COUNTTHIS" | grep run.out | grep -c "COUNTTHIS")

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
FILES = $(shell find . -name args.txt -print | grep -v .AppleDouble)

N_FILES = $(shell echo $(FILES) | tr ' ' '\n' | wc -l)
RESULTS = $(FILES:args.txt=run.out) 
OUTPUT_ALIGNMENTS = $(FILES:args.txt=labeled_reads.bam) $(FILES:args.txt=labeled_reads.bai)
RESULT_CHECKS = $(FILES:args.txt=run.out.check)
JARS = $(shell find . -mindepth 2 -name mutinack.jar -print) $(shell find . -mindepth 2 -name jacoco.jar -print)
REFFILES = $(shell find . -mindepth 2 '(' -name ref.fa -print -or -name ref.fa.fai -print ')')
LOGBACKCONF = $(shell find . -mindepth 2 -name logback.xml -print)
PROGRAM_OUTPUT = $(shell find . -name '*control_mutations.bed' -print -or -name '*control_top_bottom_disag.bed' -print -or -name '*test_mutations.bed' -print -or -name '*test_top_bottom_disag.bed' -print -or -name '*_coverage.bed' -print -or -name '*_coverage.proto' -print)

.PHONY: passed_message
passed_message: tests
	@echo "ALL" $(N_FILES) "TESTS PASSED (output alignments saved)"

.PHONY: outputAlignments
outputAlignments:
	@$(eval OUTPUT_ARGS = "-outputAlignmentFile labeled_reads.bam")
	@echo "Outputting alignments"

.PHONY: tests
tests: $(RESULTS) $(RESULT_CHECKS) | outputAlignments

#Used to force program to be re-run when the output check failed in a previous run.
#This mechanism is advantageous in that it makes it possible to keep the run.out
#files for inspection, instead of Make simply deleting them whenever a runtime or
#validation error is encountered.
FORCE_RERUN = 

FAILED_TESTS = $(shell find . -name FAILED -print)
FAILED_RESULTS = $(FAILED_TESTS:FAILED=run.out)
FAILED_CHECKS = $(FAILED_TESTS:FAILED=run.out.check)

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

LOG_AND_TEMP_FILES = $(shell find . -name '*.log' -print0 -or -name '*.swp' -print0 -or -name '*.core' -print0 -or -name '*.dict' -print0 -or -name '*.bwt' -print0 -or -name '*.bt2' -print0 -or -name '*.rbwt' -print0 -or -name '*.ann' -print0 -or -name '*.rsa' -print0 -or -name refBWA079.fa -print0 -or -name BWA_OPTIONS_FILE -print0 -or -name '*.amb' -print0 -or -name '*.rpac' -print0 -or -name '*.pac' -print0 -or -name '*.cached' -print0 -or -name '*.info' -print0 -or -name .DS_Store -print0 -or -name 'ins_stats_*' -print0 -or -name 'main_stats_*' -print0 -or -name 'coverage_*.txt' -print0 -or -name 'refSeq*' -print0 -or -name '*.sa' -print0 | xxd -p)

clean_log_and_temp_files:
	@echo "$(LOG_AND_TEMP_FILES)" | xxd -p -r | xargs -0 -n200 rm -f

clean_results:
	@echo "$(RESULTS) $(OUTPUT_ALIGNMENTS) $(PROGRAM_OUTPUT)" | xargs -n200 rm -f

clean_check:
	@echo "$(RESULT_CHECKS)" | xargs -n200 rm -f

clean_jars:
	@echo "$(JARS)" | xargs -n200 rm -f

clean_FAILED:
	@echo "$(FAILED_TESTS)" | xargs -n200 rm -f

clean_hardlinks: clean_jars
	@echo "$(REFFILES) $(LOGBACKCONF)" | xargs -n200 rm -f

#Create hardlinks to the master files present in the root functional_tests directory.
#Symbolic links lead to undesirable complications, and independent copies lead to
#undesirable I/O and disk space usage.
.PRECIOUS: %/mutinack.jar %/ref.fa %/ref.fa.fai %/ref.fa.info %/logback.xml %/jacoco.jar
%/mutinack.jar:
	@ln -f mutinack.jar $@

%/jacoco.jar:
	@ln -f ../lib/org.jacoco.agent-0.7.5.201505241946-runtime.jar $@

%/logback.xml:
	@ln -f logback.xml $@

%/ref.fa:
	@ln -f ref.fa $@

%/ref.fa.info: ref.fa.info
	@ln -f ref.fa.info $@

ref.fa.info:
	@touch ref.fa.info
	@touch -t 198001010101 ref.fa.info

%/ref.fa.fai:
	@ln -f ref.fa.fai $@

#TODO: introduce optional dependency on %/alignment-2/test1.bwa_mem_unpaired_no_adapt.map.sorted.bam
.PRECIOUS: %.out
%/run.out : %/mutinack.jar %/jacoco.jar %/ref.fa %/ref.fa.info %/ref.fa.fai %/logback.xml %/args.txt %/test_genome_locations.txt %/alignment-1/test1.bwa_mem_unpaired_no_adapt.map.sorted.bam $(FORCE_RERUN) 
	@echo _____Computing $(BUILD): $@
#collapseFilteredReads
	@(cd $* && $(PATH_TO_NG) uk.org.cinquin.mutinack.Mutinack -workingDirectory $(BASEDIR)/"$*" $(SUBMIT_TO_SERVER) $(OUTPUT_COVERAGE_PROTO) -readContigsFromFile -noStatusMessages -verbosity 3 -referenceGenome ref.fa $(shell echo $(OUTPUT_ARGS)) -sortOutputAlignmentFile true -logReadIssuesInOutputBam true -forceOutputAtPositionsFile test_genome_locations.txt $(shell cat $*/args.txt)) > $@ || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false ) 

.DELETE_ON_ERROR:
%/run.out.check: %/run.out %/expected_output.txt
#	grep -f $*/expected_output.txt $*/run.out > $@
	@(./grep_lines_in_file $*/run.out < $*/expected_output.txt > $@) && ($(GIT) rm -f $*/FAILED > /dev/null 2>&1 || rm -f $*/FAILED) || (java -jar mutinack.jar -version > $*/FAILED && $(GIT) add $*/FAILED; false )

