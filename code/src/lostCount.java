import uk.ac.ebi.brain.core.Brain;
import uk.ac.ebi.brain.error.BrainException;
import uk.ac.ebi.brain.error.ExistingClassException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mcourtot on 19/05/2016.
 * This class validates and checks that the slim terms provide enough coverage for the annotations.
 */
public class lostCount {

    public static <T> boolean contains2(final T[] array, final T v) {
        if (v == null) {
            for (final T e : array)
                if (e == null)
                    return true;
        } else {
            for (final T e : array)
                if (e == v || v.equals(e))
                    return true;
        }

        return false;
    }

    public static void main(String[] args) throws BrainException {

        if (args[0] == null) {
            System.out.println("Proper Usage is: java inputPathCountAnnotations inputPathSlim ontologyURI savePath debug");
            System.exit(0);
        }

        String inputPath = args[0];
        String slimPath = args[1];
        String ontologyURI = args[2];
        String savePath = args[3];
        Boolean debug = Boolean.parseBoolean(args[4]);

        System.out.println("Trying to read " + inputPath + ", create " + ontologyURI + " and save it as " + savePath + ". Debug is " + debug);
        //example configuration is
//        "/Users/mcourtot/Desktop/projects/metagenomics/docs/source/CC/Metagenomics_allcellterms.txt"
//        "/Users/mcourtot/Desktop/projects/metagenomics/docs/slims/CC.txt"
//        "http://www.ebi.ac.uk/metagenomics/CC_lost_all.owl"
//        "/Users/mcourtot/Desktop/projects/metagenomics/owl/CC_lost_all.owl"
//        true

        Brain brain = new Brain();
        Brain slim2 = new Brain("http://purl.obolibrary.org/obo/", ontologyURI);

        System.out.println("loading GO from the web, please wait.");
        //Load the ontology from the web
        brain.learn("http://purl.obolibrary.org/obo/go.owl");

        System.out.println("GO loaded");

        //Add a custom annotation property
        slim2.addAnnotationProperty("label_with_counts");
        slim2.addAnnotationProperty("IAO_0000115");
        slim2.addAnnotationProperty("label_slim_status");


        //read the slim classes in from the slimPath file
        BufferedReader in = null;
        try {
            //in = new BufferedReader(new FileReader("/Users/mcourtot/Desktop/projects/metagenomics/docs/slims/CC.txt"));
            in = new BufferedReader(new FileReader(slimPath));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String str;

        List<String> list = new ArrayList<String>();
        try {
            while ((str = in.readLine()) != null) {
                String new_str = str.replace(":", "_");//replace the colon by an underscore in the GO IDs
                new_str.trim();
                list.add(new_str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] existingSlimClasses = list.toArray(new String[0]);

        if (debug) for (String s : existingSlimClasses) {
            System.out.println("Class declared as in slim: " + s);
        }

        //load the input file as txt (tab delimited)
        BufferedReader br = null;
        String cvsSplitBy = "\t";

        long countIN = 0;
        long countOUT = 0;

        try {

            br = new BufferedReader(new FileReader(inputPath));
            String line;

            while ((line = br.readLine()) != null) {

                // use tab as separator
                String[] term = line.split(cvsSplitBy);

                String termID_temp = term[0];
                String termID = termID_temp.replace(":","_");//replace the colon by an underscore in the GO IDs
                String count = term[1];
                String label = term[3];
                String labelCount = label + " - " + count;
                //String slimLabel = label;


                try {

                    if (contains2(existingSlimClasses,termID))//the term ID is already one we have kept in the SLIM
                    {
                        //if (debug) System.out.println("Class already in slim: " + termID);
                        slim2.addClass(termID);
                        slim2.annotation(termID, "label_slim_status", "IN - " + labelCount);
                        countIN = countIN + Integer.parseInt(count);
                        //if (debug) System.out.println("Added class " + termID + "as IN");
                    }
                    else //get superclasses of the termID to check if one of their superclasses is in the slim
                    {
                        //Retrieves all the superclasses
                        List<String> superClasses1 = brain.getSuperClasses(termID, false);
                        Boolean IN = false;
                        if (debug) for (String superClass1 : superClasses1) {
                            //System.out.println("List of superclasses: " + superClass1);
                        }
                        //browse the list of superclasses and check whether they are in the existing slim array
                        for (String superClass1 : superClasses1) {
                            if (contains2(existingSlimClasses,superClass1))//the term ID is a subclass of one we have kept in the SLIM
                            {
                                IN = true;
                                //if (debug) System.out.println("IN is true because of superclass: " + superClass1);
                            }
                        }
                        //if (debug) System.out.println("IN is: " + IN);
                        if (IN){
                            //System.out.println("Class is a subclass of one already in slim: " + superClass1);
                            slim2.addClass(termID);
                            slim2.annotation(termID, "label_slim_status", "IN - " + labelCount);
                            countIN = countIN + Integer.parseInt(count);
                            //if (debug)  System.out.println("Added class " + termID + "as IN");
                            IN = false;
                        }
                        else //the term ID is not a subClass of one we have kept in the slim
                        {
                            //System.out.println("Class is NOT a subclass of one already in slim: " + superClass1);
                            slim2.addClass(termID);
                            slim2.annotation(termID, "label_slim_status", "OUT - " + labelCount);
                            if (debug)  System.out.println("countOUt before is " + countOUT);

                            countOUT = countOUT + Integer.parseInt(count);
                            if (debug)
                            {
                                System.out.println("Added class " + labelCount + "as OUT and class count is " + Integer.parseInt(count) + " Total count is " + countOUT);

                            }

                        }
                    }



                }catch (ExistingClassException e) {
                    e.printStackTrace();
                }



            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("Done. Saved " + savePath);
        System.out.println("Count IN =  " + countIN);
        System.out.println("Count OUT =  " + countOUT);



        //Save the ontology at the specified path
        slim2.save(savePath);



    }

}
