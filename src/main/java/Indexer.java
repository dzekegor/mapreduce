import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class Indexer {

    private static long DOCUMENT_NUMBER = 0;

    public static class TFMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        // Occurrence of some word in the in our docs
        // \\s* means unundetified number of spaces
        private final static String PATTERN = "\\s*\\b\\s*";

        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            Pattern wordPattern = Pattern.compile(PATTERN); // create pattern to extract words
            String line = value.toString().toLowerCase();

            try {
                JSONObject json = new JSONObject(line);
                String fileName = json.getString("title");

                for (String currentWord : wordPattern.split(json.getString("text"))) {
                    if (currentWord.isEmpty()) {
                        continue;
                    }
                    word = new Text(currentWord + "~~~~" + fileName);

                    // context works as a bridge bw Mapper and Reducer
                    // by writing to it we pass arguments to Reducer via our dict
                    context.write(word, one); // One because the combiner will make a huge array that we'll sum up later
                }
            } catch (JSONException je) {
                System.out.println(je.getMessage());
            }
        }
    }

    public static class TFReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;

            for (IntWritable value : values) {
                sum += value.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }

    public static class TFIDFMapper extends Mapper<LongWritable, Text, Text, Text> {

        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();

            if (!line.isEmpty()) {
                String[] words = line.split("~~~~");
                String word = words[0];
                String[] fntf = words[1].split("\t");
                String fileName = fntf[0];
                int tf = Integer.parseInt(fntf[1]);
                Text fileNameTF = new Text(fileName + "=" + tf);
                context.write(new Text(word), fileNameTF);
            }

        }
    }

    public static class TFIDFReducer extends Reducer<Text, Text, Text, DoubleWritable> {

        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Configuration tfidfconfig = context.getConfiguration();
            int numberOfDocs = 0;
            ArrayList<String> list = new ArrayList<>();
            for (Text fileNameTF : values) {
                numberOfDocs++;
                list.add(fileNameTF.toString());
            }
            double idf = Math.log10(Long.parseLong(tfidfconfig.get("DocNumber")) / numberOfDocs);
            for (String fileNameTF : list) {
                String[] fntf = fileNameTF.split("=");
                String fileName = fntf[0];
                int tf = Integer.parseInt(fntf[1]);
                Text word = new Text(key + "~~~~" + fileName);
                DoubleWritable tfidf = new DoubleWritable(tf / idf);
                context.write(word, tfidf);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Crating new configuration for MapReduce
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "TermFrequency");
        job.setJarByClass(Indexer.class);

        // Initializing our Mapper and Reducer
        job.setMapperClass(TFMapper.class);
        job.setCombinerClass(TFReducer.class);
        job.setReducerClass(TFReducer.class);

        // Initializing the Types of Dictionary: {Key: Value}
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);

        // Our input, output
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        int tfStatus = job.waitForCompletion(true) ? 0 : 1;
        if (tfStatus == 1) {
            System.exit(1);
        }
        FileSystem fileSystem = FileSystem.get(conf);
        Path pt = new Path(args[0]);
        ContentSummary contentSummary = fileSystem.getContentSummary(pt);
        DOCUMENT_NUMBER = contentSummary.getFileCount();

        Configuration newConf = new Configuration();
        newConf.set("DocNumber", String.valueOf(DOCUMENT_NUMBER));
        Job idfJob = Job.getInstance(newConf, "TFIDF");
        job.setJarByClass(Indexer.class);
        job.setMapperClass(TFIDFMapper.class);
        job.setCombinerClass(TFIDFReducer.class);
        job.setReducerClass(TFIDFReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(idfJob.waitForCompletion(true) ? 0 : 1);
    }
}