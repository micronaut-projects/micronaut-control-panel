/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.objectstorage;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.net.URI;
import java.util.Map;

@Controller("/object-storage-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class ObjectStorageController {

    @SuppressWarnings("unchecked")
    private static final Argument<ObjectStorageOperations<?, ?, ?>> ARGUMENT = (Argument<ObjectStorageOperations<?, ?, ?>>) (Argument<?>) Argument.of(ObjectStorageOperations.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"), Argument.ofTypeVariable(Object.class, "D"));

    private final Map<String, ObjectStorageOperations<?, ?, ?>> operationsMap;
    private final HttpHostResolver httpHostResolver;

    public ObjectStorageController(BeanLocator locator, final HttpHostResolver httpHostResolver) {
        this.operationsMap = locator.mapOfType(ARGUMENT);
        this.httpHostResolver = httpHostResolver;
    }

    @Get("/{objectStorage}/{key}")
    public StreamedFile download(String objectStorage, String key) {
        var operations = operationsMap.get(objectStorage);
        if (operations != null) {
            return operations.retrieve(key)
                .map(ObjectStorageEntry::toStreamedFile)
                .orElse(null);
        } else  {
            return null;
        }
    }

    @Post(value = "/{objectStorage}", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> upload(CompletedFileUpload fileUpload, String objectStorage, HttpRequest<?> request) {
        var operations = operationsMap.get(objectStorage);
        if (operations != null) {
            try {
                UploadRequest uploadRequest = UploadRequest.fromCompletedFileUpload(fileUpload, fileUpload.getFilename());
                UploadResponse<?> response = operations.upload(uploadRequest);
                return HttpResponse
                    .created(location(request, uploadRequest.getKey()))
                    .header(HttpHeaders.ETAG, response.getETag());
            } catch (ObjectStorageException e) {
                return HttpResponse.serverError(e);
            }
        } else {
            return HttpResponse.notFound();
        }
    }

    private URI location(HttpRequest<?> request, String key) {
        return UriBuilder.of(httpHostResolver.resolve(request))
            .path(key)
            .build();
    }

    @Delete("/{objectStorage}/{key}")
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
