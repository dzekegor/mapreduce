import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.codehaus.jettison.json.JSONException;

import java.io.IOException;

public class Ranking extends Configured implements Tool {
    public static void main( String[] args) throws  Exception {
        int res  = ToolRunner .run( new Ranking(), args);
        System .exit(res);
    }

    public int run( String[] args) throws  Exception {
        Job job = Job.getInstance(getConf(), " Ranker ");
        job.setJarByClass(this.getClass());
        FileInputFormat.addInputPaths(job, args[0]);
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.setMapperClass(Mapper.class);
        job.setReducerClass(Reducer.class);
        job.setOutputKeyClass(DoubleWritable.class);
        job.setOutputValueClass(Text.class);
        job.setMapOutputKeyClass(DoubleWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setSortComparatorClass(RankDescending.class);

        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class Mapper
            extends org.apache.hadoop.mapreduce.Mapper {

        public void map(LongWritable key, Text value, Context context
        ) throws IOException, InterruptedException, JSONException {

            String str = value.toString().toLowerCase();

            String arr[] = str.split("\t");
            Text inst = new Text(arr[0]);
            DoubleWritable doubWrt = new DoubleWritable(Double.parseDouble(arr[1]));
            context.write(doubWrt, inst);

        }
    }


    public static class Reducer
            extends org.apache.hadoop.mapreduce.Reducer {

        public void reduce(DoubleWritable words_arr, Iterable<Text> values,
                           Context context
        ) throws IOException, InterruptedException {
            for (Text text : values){
                context.write(text, words_arr);
            }
        }
    }

    public static class RankDescending extends WritableComparator{
        protected RankDescending() {
            super(DoubleWritable.class, true);
        }

        @Override
        public int compare(WritableComparable w1, WritableComparable w2) {
            DoubleWritable key1 = (DoubleWritable) w1;
            DoubleWritable key2 = (DoubleWritable) w2;
            if(key1.compareTo(key2) < 0)
                return 1;
            else if (key1.compareTo(key2) > 0)
                return -1;
            return 0;
        }
    }
}