package io.micronaut.controlpanel.panels.objectstorage;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.objectstorage.ObjectStorageOperations;

import java.util.Map;

@Controller("/object-storage-control-panel-controller")
public class ObjectStorageController {

    private final @NonNull Map<String, ObjectStorageOperations> operationsMap;

    public ObjectStorageController(BeanLocator locator) {
        this.operationsMap = locator.mapOfType(ObjectStorageOperations.class);
    }

    @Delete
    public HttpResponse<Void> delete(String objectStorage, String key) {
        var operations = operationsMap.get(objectStorage);
        if (operations != null) {
            operations.delete(key);
            return HttpResponse.noContent();
        }  else {
            return HttpResponse.notFound();
        }
    }
}
