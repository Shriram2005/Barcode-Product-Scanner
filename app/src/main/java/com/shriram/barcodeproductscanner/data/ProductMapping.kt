package com.shriram.barcodeproductscanner.data

/**
 * Data class representing a product mapping from CSV file
 * Structure: SKU;EAN
 * Example: 00-00070425;8901207062865
 */
data class ProductMapping(
    val sku: String,        // Product code (SKU)
    val ean: String         // Barcode (EAN)
)

/**
 * Repository for managing product mappings from CSV files
 */
class ProductMappingRepository {
    private var mappings: Map<String, String> = emptyMap()
    
    /**
     * Parse CSV content and store mappings
     * @param csvContent The content of the CSV file
     */
    fun loadMappingsFromCsv(csvContent: String) {
        try {
            val lines = csvContent.lines()
                .filter { it.isNotBlank() && !it.startsWith("_") } // Skip header and empty lines
            
            mappings = lines.mapNotNull { line ->
                val parts = line.split(";")
                if (parts.size >= 2) {
                    val sku = parts[0].trim()
                    val ean = parts[1].trim()
                    if (sku.isNotEmpty() && ean.isNotEmpty()) {
                        ean to sku // Map EAN (barcode) to SKU (product code)
                    } else null
                } else null
            }.toMap()
            
        } catch (e: Exception) {
            // If parsing fails, keep empty mappings
            mappings = emptyMap()
        }
    }
    
    /**
     * Get product code (SKU) for a given barcode (EAN)
     * @param barcode The scanned barcode
     * @return Product code if found, null otherwise
     */
    fun getProductCode(barcode: String): String? {
        return mappings[barcode]
    }
    
    /**
     * Check if any mappings are loaded
     */
    fun hasMappings(): Boolean {
        return mappings.isNotEmpty()
    }
    
    /**
     * Get total number of mappings
     */
    fun getMappingCount(): Int {
        return mappings.size
    }
    
    /**
     * Clear all mappings
     */
    fun clearMappings() {
        mappings = emptyMap()
    }
}
