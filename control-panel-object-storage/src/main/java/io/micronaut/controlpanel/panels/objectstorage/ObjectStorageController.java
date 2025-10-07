package io.micronaut.controlpanel.panels.objectstorage;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;

import java.util.Map;

@Controller("/object-storage-control-panel-controller")
public class ObjectStorageController {

    @SuppressWarnings("unchecked")
    private static final Argument<ObjectStorageOperations<?, ?, ?>> ARGUMENT = (Argument<ObjectStorageOperations<?, ?, ?>>) (Argument<?>) Argument.of(ObjectStorageOperations.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"), Argument.ofTypeVariable(Object.class, "D"));

    private final Map<String, ObjectStorageOperations<?, ?, ?>> operationsMap;

    public ObjectStorageController(BeanLocator locator) {
        this.operationsMap = locator.mapOfType(ARGUMENT);
    }

    @Get("/{objectStorage}/{key}")
    public SystemFile download(String objectStorage, String key) {
        var operations = operationsMap.get(objectStorage);
        if (operations != null) {
            return operations.retrieve(key)
                .map(ObjectStorageEntry::toSystemFile)
                .orElse(null);
        } else  {
            return null;
        }
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
