#!/usr/bin/python2.7

"""

This tool maps Gene Ontology (GO) terms (Input file, TXT format) to GO terms in Metagenomics GO slim using Map2Slim
(https://github.com/owlcollab/owltools/wiki/Map2Slim). Map2Slim is a program feature of OWLTools which
is a Java API on top of the OWL API.

This tool runs as part of the EBI Metagenomics pipeline version 3.0 (https://www.ebi.ac.uk/metagenomics/pipelines/3.0)
to summarise GO annotations. The condensed set up GO slim terms is then used for visualisation on the EBI Metagenomics
website.

"""

"""
Copyright [1999-2015] Wellcome Trust Sanger Institute and the EMBL-European Bioinformatics Institute
Copyright [2016] EMBL-European Bioinformatics Institute

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

import argparse
import os
import subprocess
import sys
import datetime
import time
import io

__author__ = "Maxim Scheremetjew (EMBL-EBI)"
__copyright__ = "Copyright [2016] EMBL-European Bioinformatics Institute"
__credits__ = ["Alex Mitchell", "Melanie Courtot", "Maxim Scheremetjew"]
__license__ = "Apache License"
__version__ = "1.0"
__maintainer__ = "Maxim Scheremetjew"
__email__ = "maxim@ebi.ac.uk"
__status__ = "Prototype"

req_version = (2, 7)
cur_version = sys.version_info
if cur_version < req_version:
    print "Your Python interpreter is too old. You need version 2.7.x"  # needed for argparse
    sys.exit()


class GOSummaryUtils(object):
    @classmethod
    def __pathExists(self, path, delay=30):
        """Utility method that checks if a file or directory exists, accounting for NFS delays
           If there is a delay in appearing then the delay is logged
        """
        startTime = datetime.datetime.today()
        while not os.path.exists(path):
            currentTime = datetime.datetime.today()
            timeSoFar = currentTime - startTime
            if timeSoFar.seconds > delay:
                return False
            time.sleep(1)
        endTime = datetime.datetime.today()
        totalTime = endTime - startTime
        # if totalTime.seconds > 1:
        #    print "Pathop: Took", totalTime.seconds, "to determine that path ",path, "exists"
        return True

    @classmethod
    def __fileOpen(self, fileName, fileMode, buffer=0):
        """File opening utility that accounts for NFS delays
           Logs how long each file-opening attempt takes
           fileMode should be 'r' or 'w'
        """
        startTime = datetime.datetime.today()
        # print "Fileop: Trying to open file", fileName,"in mode", fileMode, "at", startTime.isoformat()
        if fileMode == 'w' or fileMode == 'wb':
            fileHandle = open(fileName, fileMode)
            fileHandle.close()
        while not os.path.exists(fileName):
            currentTime = datetime.datetime.today()
            timeSoFar = currentTime - startTime
            if timeSoFar.seconds > 30:
                print "Fileop: Took more than 30s to try and open", fileName
                print "Exiting"
                sys.exit(1)
            time.sleep(1)
        try:
            fileHandle = open(fileName, fileMode, buffer)
        except IOError as e:
            print "I/O error writing file{0}({1}): {2}".format(fileName, e.errno, e.strerror)
            print "Exiting"
            sys.exit(1)
        endTime = datetime.datetime.today()
        totalTime = endTime - startTime
        # print "Fileop: Opened file", fileName, "in mode", fileMode, "in", totalTime.seconds, "seconds"
        return fileHandle

    @staticmethod
    def parse_mapped_gaf_file(gaf_file):
        """
        parse_mapped_gaf_file(gaf_file) -> dictionary

        Example of GAF mapped output:
            !gaf-version: 2.0
            ! This GAF has been mapped to a subset:
            ! Subset: user supplied list, size = 38
            ! Number of annotation in input set: 1326
            ! Number of annotations rewritten: 120
            EMG	GO:0005839	GO		GO:0005839	PMID:12069591	IEA		C			protein	taxon:1310605	20160528	InterPro
            EMG	GO:0000160	GO		GO:0005575	PMID:12069591	IEA		C			protein	taxon:1310605	20160528	InterPro

        Parsing the above GAF file will create the following dictionary:
        result = {'GO:0005839':'GO:0005839', 'GO:0000160':'GO:0005575'}

        :param gaf_file:
        :return:
        """
        result = {}
        if GOSummaryUtils.__pathExists(gaf_file):
            handle = GOSummaryUtils.__fileOpen(gaf_file, "r")
            for line in handle:
                if not line.startswith("!"):
                    line = line.strip()
                    splitted_line = line.split("\t")
                    go_id = splitted_line[1]
                    mapped_go_id = splitted_line[4]
                    result.setdefault(go_id, set()).add(mapped_go_id)
        return result

    @staticmethod
    def create_gaf_file(gaf_input_file_path, go_id_set):
        """

        :param gaf_input_file_path:
        :param go2proteinDict:
        :return: nothing
        """
        with io.open(gaf_input_file_path, 'w') as file:
            file.write(u'!gaf-version: 2.1\n')
            file.write(u'!Project_name: EBI Metagenomics\n')
            file.write(u'!URL: http://www.ebi.ac.uk/metagenomics\n')
            file.write(u'!Contact Email: metagenomics-help@ebi.ac.uk\n')
            file.write(u'!GAF file generated automatically by the GO slim mapper tool\n')
            for go_id in go_id_set:
                gaf_file_entry_line_str = 'EMG\t{0}\t{1}\t\t{2}\tPMID:12069591\tIEA\t\t{3}\t\t\tprotein\ttaxon:1310605\t{4}\t{5}\t\t'.format(
                        go_id,
                        'GO',
                        go_id,
                        'C',
                        '20160913',
                        'EBIMetagenomics')
                file.write(u'' + gaf_file_entry_line_str + '\n')

    @staticmethod
    def count_and_assign_go_annotations(go2protein_count, go_annotations, num_of_proteins):
        for go_id in go_annotations:
            count = go2protein_count.setdefault(go_id, 0)
            count += 1 * num_of_proteins
            go2protein_count[go_id] = count


def run_map2slim(owltools_bin, core_gene_ontology_obo_file, metagenomics_go_slim_ids_file,
                 gaf_input_full_path, gaf_output_full_path):
    try:
        output = subprocess.check_output(
                [
                    owltools_bin,
                    core_gene_ontology_obo_file,
                    '--gaf',
                    gaf_input_full_path,
                    '--map2slim',
                    '--idfile',
                    metagenomics_go_slim_ids_file,
                    '--write-gaf',
                    gaf_output_full_path
                ], stderr=subprocess.STDOUT, )
        # print output
    except subprocess.CalledProcessError, ex:
        print "--------error------"
        print ex.cmd
        print ex.message
        print ex.returncode
        print ex.output
        raise
    except:
        print "Unexpected error:", sys.exc_info()[0]
        raise


if __name__ == '__main__':
    description = "EBI Metagenomics Go slim mapper."
    # Set default value for the current working directory (script path)
    script_pathname = os.path.dirname(sys.argv[0])
    script_full_path = os.path.abspath(script_pathname)
    #    Parse script parameters
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("-i", "--input_file",
                        type=str,
                        help="List of GO terms you want to map to Metagenomics GO slim. TXT format. One term per line.",
                        required=True)
    parser.add_argument("-wd", "--directory",
                        type=str,
                        help="Working directory",
                        required=False,
                        default=''.join([script_full_path, '/']))
    parser.add_argument("-ob", "--obo_file",
                        type=str,
                        help="Absolute path to full Gene Ontology OBO file.",
                        required=False,
                        default=''.join([script_full_path, "/go-basic-metagenomics_release_20160705.obo"]))
    parser.add_argument("-s", "--slims",
                        type=str,
                        help="Absolute path to Metagenomics GO slim terms TXT file.",
                        required=False,
                        default=''.join([script_full_path, "/metagenomics_go_slim_ids.txt"]))
    parser.add_argument("-bin", "--owltools",
                        type=str,
                        help="Path to OWLTools binary.",
                        required=False,
                        default=''.join([script_full_path, "/owltools/owltools"]))
    args = parser.parse_args()

    print "INFO: " + description

    # Get program configuration
    input_file = args.input_file

    temp_dir = args.directory
    # path to the latest version of the core gene ontology in OBO format
    full_gene_ontology_obo_formatted = args.obo_file

    # Map2Slim program parameters
    metagenomics_go_slim_ids_file = args.slims
    owltools_bin = args.owltools

    gaf_input_file_path = temp_dir + 'pipeline_input_annotations.gaf'
    gaf_output_file_path = temp_dir + 'pipeline_mapped_annotations.gaf'

    # Generating the GAF input file for Map2Slim
    print "Generating the GAF input file for Map2Slim..."

    go_id_set = []
    with open(input_file, "r") as f:
        for line in f:
            go_id_set.append(line.strip())
    # go_id_set = {'GO:0017038', 'GO:0016020', 'GO:0016773', 'GO:0005975', 'GO:0005975', 'GO:0016773'}
    GOSummaryUtils.create_gaf_file(gaf_input_file_path, set(go_id_set))
    del go_id_set
    print "Finished GAF file generation."

    # Generate GO slim - runs the Map2Slim option from the owltools
    # Run Map2Slim for more information on how to use the tool see https://github.com/owlcollab/owltools/wiki/Map2Slim
    print "Running Map2Slim now..."
    run_map2slim(owltools_bin, full_gene_ontology_obo_formatted, metagenomics_go_slim_ids_file,
                 gaf_input_file_path, gaf_output_file_path)
    print "Map2Slim finished!"

    print "Parsing mapped annotations..."
    go2mapped_go = GOSummaryUtils.parse_mapped_gaf_file(gaf_output_file_path)
    print "Finished parsing."
    print "<===========Results:"
    for key in go2mapped_go.keys():
        print key + " mapped to [" + ", ".join(str(mapped_go_id) for mapped_go_id in go2mapped_go.get(key)) + "]"

    print "Program finished."
