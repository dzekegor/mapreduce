import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;


public class Search extends Configured implements Tool {
    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(new Search(), args);
        System.exit(res);
    }

    public int run(String[] args) throws Exception {
        Job job = Job.getInstance(getConf(), "Search");
        job.setJarByClass(this.getClass());
        job.getConfiguration().set("Query", args[2]);
        FileInputFormat.addInputPaths(job, args[0]);
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.setMapperClass(Map.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);

        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class Map extends Mapper<LongWritable, Text, Text, DoubleWritable> {

        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {

            String str = value.toString().toLowerCase();

            if (!str.isEmpty()) {
                String arr[] = str.split("~~~~");
                String arr_search[] = context.getConfiguration().get("Query").split(" ");
                for (String s : arr_search) {
                    if (arr[0].equals(s.toLowerCase())) {
                        String arr2[] = arr[1].split("\t");
                        Text actual = new Text(arr2[0]);
                        DoubleWritable word_count = new DoubleWritable(Double.parseDouble(arr2[1]));
                        context.write(actual, word_count);
                    }
                }
            }
        }
    }
    public static class Reduce extends org.apache.hadoop.mapreduce.Reducer {
        public void reduce(Text key,  Iterable<DoubleWritable > total,  Context context)
                throws IOException,  InterruptedException {
            double sum  = 0;
            for (DoubleWritable tot : total) {
                sum  += tot.get();
            }
            context.write(key,  new DoubleWritable(sum));
        }
    }
}
