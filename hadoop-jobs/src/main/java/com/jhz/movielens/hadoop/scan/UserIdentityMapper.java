package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.UserRecord;
import com.jhz.movielens.hadoop.parse.MovieLensEncoding;
import com.jhz.movielens.hadoop.parse.ParseResult;
import com.jhz.movielens.hadoop.parse.UserParser;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public final class UserIdentityMapper extends Mapper<LongWritable, Text, Text, Text> {
    private final UserParser parser = new UserParser();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        ParseResult<UserRecord> result = parser.parse(MovieLensEncoding.decodeIso88591(value));
        if (!result.isValid()) {
            return;
        }
        UserRecord user = result.value();
        context.getCounter(QualityCounterNames.RECORD_GROUP,
                QualityCounterNames.record("users", "relationallyChecked")).increment(1);
        context.write(
                new Text("users|" + user.userId()),
                new Text(user.gender() + '|' + user.age() + '|' + user.occupation() + '|' + user.zipCode()));
    }
}
