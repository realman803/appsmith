package com.appsmith.server.migrations.db.ce;

import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.QApplication;
import com.appsmith.server.domains.QApplicationDetail;
import com.appsmith.server.migrations.utils.CompatibilityUtils;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static com.appsmith.server.repositories.ce.BaseAppsmithRepositoryCEImpl.fieldName;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@ChangeUnit(order = "025", id = "rename-appPositioning-property")
public class Migration025RenameAppPositioningProperty {
    private final MongoTemplate mongoTemplate;

    @RollbackExecution
    public void rollbackExecution() {}

    @Execution
    public void executeMigration() {
        buildQueryAndExecute(QApplication.application.unpublishedApplicationDetail, Boolean.FALSE);
        buildQueryAndExecute(QApplication.application.publishedApplicationDetail, Boolean.TRUE);
    }

    private void buildQueryAndExecute(QApplicationDetail baseLocation, Boolean published) {
        Criteria nonDeletedAppCriteria =
                where(fieldName(QApplication.application.deletedAt)).isNull();
        Criteria appPositioningCriteria = where(fieldName(baseLocation) + "." + fieldName(baseLocation.appPositioning))
                .exists(true);

        final Query applicationDetailQuery =
                query((new Criteria()).andOperator(nonDeletedAppCriteria, appPositioningCriteria));
        applicationDetailQuery
                .fields()
                .include("application.id")
                .include(fieldName(baseLocation) + "." + fieldName(baseLocation.appPositioning));

        Query applicationDetailQueryOptimised = CompatibilityUtils.optimizeQueryForNoCursorTimeout(
                mongoTemplate, applicationDetailQuery, Application.class);

        mongoTemplate.stream(applicationDetailQueryOptimised, Application.class).forEach(application -> {
            final Update update = new Update();
            Application.AppPositioning appPositioning;
            if (Boolean.FALSE.equals(published)) {
                appPositioning = application.getUnpublishedApplicationDetail().getAppPositioning();
            } else {
                appPositioning = application.getPublishedApplicationDetail().getAppPositioning();
            }

            if (appPositioning != null) {
                update.set(fieldName(baseLocation) + "." + fieldName(baseLocation.layoutSystem), appPositioning);
                update.unset(fieldName(baseLocation) + "." + fieldName(baseLocation.appPositioning));
            }

            mongoTemplate.updateFirst(
                    query(where(fieldName(QApplication.application.id)).is(application.getId())),
                    update,
                    Application.class);
        });
    }
}