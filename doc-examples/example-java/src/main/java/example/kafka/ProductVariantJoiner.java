package example.kafka;

import dev.thriving.poc.ecommerce.avro.ProductVariant;
import dev.thriving.poc.ecommerce.avro.ProductVariantDetail;
import dev.thriving.poc.ecommerce.avro.ProductVariantPriceAndStock;
import org.apache.kafka.streams.kstream.ValueJoiner;

public class ProductVariantJoiner implements ValueJoiner<ProductVariantDetail, ProductVariantPriceAndStock, ProductVariant> {
    @Override
    public ProductVariant apply(ProductVariantDetail productVariantDetail, ProductVariantPriceAndStock productVariantPriceAndStock) {
        return null;
    }
}
