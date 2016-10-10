/**
 * Created by mcourtot on 10/05/2016.
 */

import uk.ac.ebi.brain.core.Brain;
import uk.ac.ebi.brain.error.BrainException;
import uk.ac.ebi.brain.error.ClassExpressionException;
import uk.ac.ebi.brain.error.ExistingClassException;
import uk.ac.ebi.brain.error.NonExistingEntityException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class addCounts {




public static void getSuperClasses(String termID, Brain brain, Brain slim, Boolean debug) throws ClassExpressionException, NonExistingEntityException {
    //Retrieves all the superclasses
    List<String> superClasses1 = brain.getSuperClasses(termID, true);


    for (String superClass1 : superClasses1) {
        String label = null;
        if (debug)  System.out.println("Superclass is " + superClass1 + ", " + brain.getLabel(superClass1));
                try {

                    try
                    {
                        label = slim.getLabel(superClass1);
                    }
                    catch (Exception e) {
                        if (debug) System.out.println ("Class doesn't exist in slim " + e + "label is : " + label);
                    }

                    if (label!= null && !label.isEmpty())
                    {
                        if (debug) System.out.println ("Class " + superClass1 + " already exists in slim. Trying to assert subclass " + termID);
                        try {
                            slim.subClassOf(termID, superClass1);
                            if (debug)
                                System.out.println("Term " + termID + " has been asserted as subclass of " + superClass1);
                        } catch (Exception e) {
                            if (debug) System.out.println ("Caught an exception when trying to assert " + termID + "as subclass of " + superClass1 + " : " + e);
                        }
                    }
                    else{
                        slim.addClass(superClass1);
                        String newLabel = brain.getLabel(superClass1);
                        slim.label(superClass1, newLabel);
                        String definition = brain.getAnnotation(superClass1, "IAO_0000115");
                        slim.annotation(superClass1, "IAO_0000115", definition);
                        //slim.annotation(superClass1, "label_with_counts", newLabel); //if we want to add the label AP in the label_with_count AP for those classes that do not have a count
                        slim.subClassOf(termID, superClass1);
                        if (debug)  System.out.println("Getting superClass of superClass " + superClass1);
                        getSuperClasses(superClass1, brain, slim, debug);
                    }


                } catch (Exception e) {
                    if (debug) System.out.println ("Caught an exception" + e);
                }

        }

}

    public static void main(String[] args) throws BrainException {

        if(args[0] == null)
        {
            System.out.println("Proper Usage is: java inputPath ontologyURI savePath");
            System.exit(0);
        }

        String inputPath = args[0];
        String ontologyURI = args[1];
        String savePath = args[2];
        Boolean debug = Boolean.parseBoolean(args[3]);

        System.out.println("Trying to read " + inputPath + ", create "+ ontologyURI + " and save it as " + savePath + ". Debug is " + debug);

        Brain brain = new Brain();
        Brain slim = new Brain("http://purl.obolibrary.org/obo/",ontologyURI);

        System.out.println("loading GO from the web, please wait.");
        //Load the ontology from the web
        brain.learn("http://purl.obolibrary.org/obo/go.owl");

        System.out.println("GO loaded");

        //Add a custom annotation property
        slim.addAnnotationProperty("label_with_counts");
        slim.addAnnotationProperty("IAO_0000115");

        //load the input file as txt (tab delimited)
        String csvFile = inputPath;
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = "\t";

        try {

            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {

                // use tab as separator
                String[] term = line.split(cvsSplitBy);

                String termID_temp = term[0];
                String termID = termID_temp.replace(":","_");//replace the colon by an underscore in the GO IDs
                String count = term[1];
                String label = term[2];
                String labelCount = label + " - " + count;

                try {
                    System.out.println("Adding new class to slim: " + termID);
                    slim.addClass(termID);
                    //slim.label(termID,label);

                }catch (ExistingClassException e) {

                }

                try {
                slim.annotation(termID, "label_with_counts", labelCount);

                slim.label(termID, brain.getLabel(termID));
                String definition = brain.getAnnotation(termID, "IAO_0000115");
                slim.annotation(termID, "IAO_0000115", definition);



                if (debug)  System.out.println("Term [ID=" + termID + "label=" + label
                        + " , name=" + count + "]");

                //Retrieves all the superclasses
                getSuperClasses(termID, brain, slim, debug);

                }catch (Exception e) {
                    if (debug) System.out.println ("Caught an exception in main: "  + e);
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


      //read the OWL file. If not label_with_counts, add label as label with counts
        List<String> slimClasses = slim.getSubClasses("Thing", false);
        for (String slimClass : slimClasses) {
            String labelCount = null;
            String label = null;
            try {
                label = slim.getLabel(slimClass);
            }
            catch (Exception e) {
                if (debug) System.out.println ("Class doesn't exist in GO: " + slimClass);
            }
            try
            {
                labelCount = slim.getAnnotation(slimClass, "label_with_counts");
            }
            catch (Exception e) {
                if (debug) System.out.println ("Class doesn't exist in slim " + e + "label is : " + label);
            }
            if (labelCount!= null && !labelCount.isEmpty())
            {
                if (debug) System.out.println ("Class " + slimClass + "already has a count");
            }
            else{

                slim.annotation(slimClass, "label_with_counts", label); //if we want to add the label AP in the label_with_count AP for those classes that do not have a count
                if (debug) System.out.println ("Added label for class " + slimClass);

            }



        }



        System.out.println("Done. Saved " + savePath);



        //Save the ontology at the specified path
       slim.save(savePath);


    }


}
