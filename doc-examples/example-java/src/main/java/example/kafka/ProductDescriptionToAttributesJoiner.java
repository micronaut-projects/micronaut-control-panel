package example.kafka;

import dev.thriving.poc.ecommerce.avro.ProductAttributeList;
import dev.thriving.poc.ecommerce.avro.ProductDescription;
import dev.thriving.poc.ecommerce.avro.ProductFullContext;
import org.apache.kafka.streams.kstream.ValueJoiner;

public class ProductDescriptionToAttributesJoiner implements ValueJoiner<ProductDescription, ProductAttributeList, ProductFullContext> {
    @Override
    public ProductFullContext apply(ProductDescription productDescription, ProductAttributeList productAttributeList) {
        return null;
    }
}
