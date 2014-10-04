package main;

import edu.uci.ics.jung.graph.DelegateTree;
import exceptions.AnalysisException;
import exceptions.PdfGenerationException;
import graph.Edge;
import graph.FileNode;
import graph.analysis.FileCountAnalyser;
import graph.analysis.FileTypeCountAnalyser;
import graph.analysis.TreeAnalyser;
import graph.analysis.TreeAnalyserRunnable;
import graph.factory.JungGraphFactory;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.*;

/**
 * Created by conor on 06/09/2014.
 */
public class Runner {

    public Runner(String path, String logPath, List<String> ignores, List<String> typeFilters, int maxDepth,
                  String analysers) throws IOException, AnalysisException {

        // Set correct options & create a factory
        JungGraphFactory.Options options = new JungGraphFactory.Options.Builder()
                .ignoreList(ignores)
                .typeFilters(typeFilters)
                .maxDepth(maxDepth).build();
        JungGraphFactory factory = new JungGraphFactory(options);

        // Use the configured factory to read in the filesystem & create a graph
        DelegateTree<FileNode, Edge> tree = (path == null) ?
                factory.generateFsGraph(FileSystems.getDefault()) : factory.generateFsGraph(path);

        // Read in the 'analyser' tokens and create the analyser list
        List<TreeAnalyser> tas = new ArrayList<>();
        for (String s : analysers.split(",")) {
            switch (s.toLowerCase().trim()) {
                // Add other matching cases here and they drop down
                case "filetypecount":
                    tas.add(new FileTypeCountAnalyser(tree, path));
                    break;
                case "filecount":
                    tas.add(new FileCountAnalyser(tree, path));
                    break;
                // TODO match on class type using reflection
            }
        }

        // Run the analyses as threads
        Map<TreeAnalyser, Thread> threads = new HashMap<>();
        for (TreeAnalyser analyser : tas) {
            Thread t = new Thread(new TreeAnalyserRunnable(analyser));
            threads.put(analyser, t);
            t.start();
        }

        // Wait for all threads to finish, add to a list of pdf streams
        List<ByteArrayOutputStream> pdfStreams = new ArrayList<>();
        for (AbstractMap.Entry<TreeAnalyser, Thread> entry : threads.entrySet()) {
            try {
                entry.getValue().join();
                pdfStreams.add(entry.getKey().generatePdfReport());
            } catch (InterruptedException e) {
                System.err.println("Interrupted Exception");
            } catch (PdfGenerationException p) {
                System.err.println("Error generating pdf for: " + entry.getKey().getAnalysisName());
            }
        }

        // Merge and print document
        PDFMergerUtility mergeUtil = new PDFMergerUtility();
        pdfStreams.forEach((pdfStream) -> mergeUtil.addSource(new ByteArrayInputStream(pdfStream.toByteArray())));

        try {
            mergeUtil.setDestinationFileName(logPath);
            mergeUtil.mergeDocuments();
        } catch (COSVisitorException e) {
            System.err.println("Error merging the document - sorry!");
        }

        System.out.println("Finished! Your report is ready at path: " + logPath);
    }


    /**
     * USAGE:
     * <p>
     * run --path <path> --maxDepth <max> --ignore<commaseplist> --typeFilter<commaseplist> --logpath <path>
     * --analysers <comseplist>
     *
     * @param args
     * @throws IOException
     * @throws AnalysisException
     */
    public static void main(String[] args) throws IOException, AnalysisException {

        String path = null;
        String logPath = null;
        String analysers = "";
        int maxDepth = 1000;
        List<String> ignores = null;
        List<String> typeFilters = null;

        // Read args
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i].toLowerCase()) {
                case "--path":
                    path = args[i + 1];
                    break;
                case "--maxdepth":
                    maxDepth = Integer.parseInt(args[i + 1]);
                    break;
                case "--ignore":
                    ignores = Arrays.asList(args[i + 1].split(","));
                    break;
                case "--typefilter":
                    typeFilters = Arrays.asList(args[i + 1].split(","));
                    break;
                case "--logpath":
                    logPath = args[i + 1];
                    break;
                case "--analysers":
                    analysers = args[i + 1];
                    break;
                default:
                    break;
            }
        }

        // Validate args
        // TODO validate args

        // Run
        Runner r = new Runner(path, logPath, ignores, typeFilters, maxDepth, analysers);
    }

}
