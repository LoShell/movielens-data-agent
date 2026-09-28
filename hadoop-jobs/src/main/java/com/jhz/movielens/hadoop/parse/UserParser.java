package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.model.UserRecord;
import com.jhz.movielens.hadoop.quality.IssueCode;
import com.jhz.movielens.hadoop.quality.QualityIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class UserParser implements MovieLensLineParser<UserRecord> {
    private static final Set<String> ALLOWED_GENDERS = Set.of("M", "F");
    private static final Set<Integer> ALLOWED_AGES = Set.of(1, 18, 25, 35, 45, 50, 56);

    @Override
    public ParseResult<UserRecord> parse(String line) {
        List<QualityIssue> issues = new ArrayList<>();
        String[] fields = ParserSupport.split(line, 5, issues);
        if (fields == null) {
            return new ParseResult<>(null, issues);
        }

        Integer userId = ParserSupport.integer(fields[0], "userId", issues);
        String gender = ParserSupport.requiredText(fields[1], "gender", issues);
        Integer age = ParserSupport.integer(fields[2], "age", issues);
        Integer occupation = ParserSupport.integer(fields[3], "occupation", issues);
        String zipCode = ParserSupport.requiredText(fields[4], "zipCode", issues);

        if (userId == null || gender == null || age == null || occupation == null || zipCode == null) {
            return new ParseResult<>(null, issues);
        }

        ParserSupport.requirePositiveIdentifier(userId, "userId", issues);
        if (!ALLOWED_GENDERS.contains(gender)) {
            issues.add(QualityIssue.error(
                    IssueCode.INVALID_GENDER, "gender", "Gender must be M or F."));
        }
        if (!ALLOWED_AGES.contains(age)) {
            issues.add(QualityIssue.error(
                    IssueCode.INVALID_AGE_CATEGORY, "age", "Unknown MovieLens age category: " + age));
        }
        if (occupation < 0 || occupation > 20) {
            issues.add(QualityIssue.error(
                    IssueCode.INVALID_OCCUPATION_CATEGORY,
                    "occupation",
                    "Occupation category must be between 0 and 20."));
        }

        return new ParseResult<>(new UserRecord(userId, gender, age, occupation, zipCode), issues);
    }
}
