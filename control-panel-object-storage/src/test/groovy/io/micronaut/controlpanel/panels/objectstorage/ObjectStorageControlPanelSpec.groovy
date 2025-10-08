package io.micronaut.controlpanel.panels.objectstorage

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import spock.lang.Specification

import java.nio.file.Path

class ObjectStorageControlPanelSpec extends Specification {

    private ObjectStorageControlPanel createPanel(ObjectStorageOperations operations,
                                                 AbstractObjectStorageConfiguration configuration,
                                                 ControlPanelConfiguration controlPanelConfig) {
        new ObjectStorageControlPanel(operations, configuration, controlPanelConfig)
    }

    private ObjectStorageEntry createMockEntry(String key) {
        def entry = Mock(ObjectStorageEntry)
        entry.key >> key
        entry
    }

    void "it has correct name"() {
        given:
        def configuration = Mock(AbstractObjectStorageConfiguration)
        configuration.getName() >> "test-storage"

        when:
        def panel = createPanel(Mock(ObjectStorageOperations), configuration, Mock(ControlPanelConfiguration))

        then:
        panel.getName() == "object-storage-test-storage"
    }

    void "it has correct title"() {
        given:
        def configuration = Mock(AbstractObjectStorageConfiguration)
        configuration.getName() >> "test-storage"

        when:
        def panel = createPanel(Mock(ObjectStorageOperations), configuration, Mock(ControlPanelConfiguration))

        then:
        panel.getTitle() == "test-storage"
    }

    void "it has correct icon"() {
        when:
        def panel = createPanel(Mock(ObjectStorageOperations), Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getIcon() == "fa-hard-drive"
    }

    void "it has correct body view"() {
        when:
        def panel = createPanel(Mock(ObjectStorageOperations), Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getBodyView().file() == "/views/object-storage/body"
    }

    void "it has correct detailed view"() {
        when:
        def panel = createPanel(Mock(ObjectStorageOperations), Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getDetailedView().file() == "/views/object-storage/detail"
    }

    void "it has correct category"() {
        when:
        def panel = createPanel(Mock(ObjectStorageOperations), Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getCategory().id() == "object-storage"
        panel.getCategory().name() == "Object Storage"
        panel.getCategory().iconClass() == "fa-hard-drive"
    }

    void "getBody returns entries from operations"() {
        given:
        def operations = Mock(ObjectStorageOperations)
        def entry1 = createMockEntry("entry1")
        def entry2 = createMockEntry("entry2")

        operations.listObjects() >> [entry1.key, entry2.key]
        operations.retrieve(entry1.key) >> Optional.of(entry1)
        operations.retrieve(entry2.key) >> Optional.of(entry2)

        when:
        def panel = createPanel(operations, Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))
        def body = panel.getBody()

        then:
        body.entries().size() == 2
        body.entries().contains(entry1)
        body.entries().contains(entry2)
    }

    void "getBody filters out entries that cannot be retrieved"() {
        given:
        def operations = Mock(ObjectStorageOperations)
        def entry1 = createMockEntry("entry1")
        def entry2 = createMockEntry("entry2")

        operations.listObjects() >> [entry1.key, entry2.key]
        operations.retrieve(entry1.key) >> Optional.of(entry1)
        operations.retrieve(entry2.key) >> Optional.empty()

        when:
        def panel = createPanel(operations, Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))
        def body = panel.getBody()

        then:
        body.entries().size() == 1
        body.entries().contains(entry1)
        !body.entries().contains(entry2)
    }

    void "getBadge returns number of objects as string"() {
        given:
        def operations = Mock(ObjectStorageOperations)
        operations.listObjects() >> [Mock(ObjectStorageEntry), Mock(ObjectStorageEntry), Mock(ObjectStorageEntry)]

        when:
        def panel = createPanel(operations, Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getBadge() == "3"
    }

    void "getBadge returns zero when no objects"() {
        given:
        def operations = Mock(ObjectStorageOperations)
        operations.listObjects() >> []

        when:
        def panel = createPanel(operations, Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))

        then:
        panel.getBadge() == "0"
    }

    void "computeMetadata returns empty map for non-local configuration"() {
        when:
        def panel = createPanel(Mock(ObjectStorageOperations), Mock(AbstractObjectStorageConfiguration), Mock(ControlPanelConfiguration))
        def metadata = panel.computeMetadata()

        then:
        metadata.isEmpty()
    }

    void "computeMetadata includes path for LocalStorageConfiguration"() {
        given:
        def operations = Mock(ObjectStorageOperations)
        def controlPanelConfig = Mock(ControlPanelConfiguration)
        def localConfig = Mock(LocalStorageConfiguration)
        localConfig.getPath() >> Path.of("/tmp/test-storage")
        def panel = new ObjectStorageControlPanel(operations, localConfig, controlPanelConfig)

        when:
        def metadata = panel.computeMetadata()

        then:
        metadata.size() == 1
        metadata.get("path") == Path.of("/tmp/test-storage")
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(ObjectStorageControlPanel.ENABLED_PROPERTY): false])

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(ObjectStorageControlPanel.NAME))

        then:
        !cfg.enabled

        cleanup:
        ctx.stop()
    }

    void "creates multiple panels for multiple object storage configurations"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.control-panel.panels.object-storage.enabled' : true,
            'micronaut.object-storage.local.storage1.path': '/tmp/storage1',
            'micronaut.object-storage.local.storage2.path': '/tmp/storage2'
        ])

        when:
        def panels = ctx.getBeansOfType(ObjectStorageControlPanel)

        then:
        panels.size() == 2
        panels*.name.contains("object-storage-storage1")
        panels*.name.contains("object-storage-storage2")

        cleanup:
        ctx.stop()
    }
}
