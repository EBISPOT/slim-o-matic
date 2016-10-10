

import uk.ac.ebi.brain.core.Brain;
import uk.ac.ebi.brain.error.BrainException;


import java.util.List;

/**
 * Created by mcourtot on 20/05/2016.
 */
public class addLabels {

    public static void main(String[] args) throws BrainException {

        if (args[0] == null) {
            System.out.println("Proper Usage is: java ontologyURI savePath");
            System.exit(0);
        }

        String ontologyURI = args[0];
        String savePath = args[1];
        Boolean debug = Boolean.parseBoolean(args[2]);

        System.out.println("Trying to read " + ontologyURI + " and save it as " + savePath + ". Debug is " + debug);

        Brain slim = new Brain();

        System.out.println("loading " + ontologyURI);

        slim.learn(ontologyURI);

        System.out.println( ontologyURI + "loaded");

        //read the OWL file. If not label_with_counts, add label as label with counts
        List<String> slimClasses = slim.getSubClasses("Thing", false);
        for (String slimClass : slimClasses) {
            String labelCount = null;
            String label = null;
            try {
                label = slim.getLabel(slimClass);
            } catch (Exception e) {
                if (debug) System.out.println("Class doesn't exist in GO: " + slimClass);
            }
            try {
                labelCount = slim.getAnnotation(slimClass, "label_with_counts");
            } catch (Exception e) {
                if (debug) System.out.println("Class doesn't exist in slim " + e + "label is : " + label);
            }
            if (labelCount != null && !labelCount.isEmpty()) {
                if (debug) System.out.println("Class " + slimClass + "already has a count");
            } else {

                slim.annotation(slimClass, "label_with_counts", label); //if we want to add the label AP in the label_with_count AP for those classes that do not have a count
                if (debug) System.out.println("Added label for class " + slimClass);

            }


        }
        //Save the ontology at the specified path
        slim.save(savePath);

        System.out.println("Done. Saved " + savePath);
    }
}
