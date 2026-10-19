package com.jhz.movielens.web.config;

import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.web.agent.IterationTwoTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IterationTwoConfiguration {
    @Bean Tool highRatingLogisticRegression(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("predict_high_rating_lr", "classification", "logistic_regression", ml, pipeline);
    }
    @Bean Tool highRatingDecisionTree(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("predict_high_rating_tree", "classification", "decision_tree", ml, pipeline);
    }
    @Bean Tool userKMeans(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("cluster_users_kmeans", "clustering", "kmeans", ml, pipeline);
    }
    @Bean Tool userGaussianMixture(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("cluster_users_gmm", "clustering", "gmm", ml, pipeline);
    }
    @Bean Tool userPca(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("reduce_user_profiles_pca", "reduction", "pca", ml, pipeline);
    }
    @Bean Tool userNmf(MlProperties ml, PipelineProperties pipeline) {
        return new IterationTwoTool("reduce_user_profiles_nmf", "reduction", "nmf", ml, pipeline);
    }
}
