#!/bin/bash
# Cleanup script for removing unused files from figma-to-design-service
# Run from project root: bash cleanup-unused-files.sh

echo "🧹 Cleaning up unused components..."

# Remove old duplicated DTOs (moved to dto/factory/)
echo "Removing old factory/dto files..."
rm -f src/main/java/com/figma/design/factory/dto/PageCreationDto.java
rm -f src/main/java/com/figma/design/factory/dto/ComponentCreationDto.java
rm -f src/main/java/com/figma/design/factory/dto/FrameCreationDto.java
rmdir src/main/java/com/figma/design/factory/dto 2>/dev/null || true

# Remove AI/Similarity auxiliary features (not core to design-to-web workflow)
echo "Removing AI Recommendation components..."
rm -f src/main/java/com/figma/design/service/AIRecommendationService.java
rm -f src/main/java/com/figma/design/service/impl/AIRecommendationServiceImpl.java
rm -f src/main/java/com/figma/design/controller/AIRecommendationController.java
rm -f src/main/java/com/figma/design/repository/AIRecommendationRepository.java
rm -f src/main/java/com/figma/design/model/AIRecommendation.java

echo "Removing Similarity Result components..."
rm -f src/main/java/com/figma/design/service/SimilarityResultService.java
rm -f src/main/java/com/figma/design/service/impl/SimilarityResultServiceImpl.java
rm -f src/main/java/com/figma/design/controller/SimilarityResultController.java
rm -f src/main/java/com/figma/design/repository/SimilarityResultRepository.java
rm -f src/main/java/com/figma/design/model/SimilarityResult.java

echo "Removing Enums..."
rm -f src/main/java/com/figma/design/model/Severity.java
rm -f src/main/java/com/figma/design/model/SimilarityStatus.java

echo "✅ Cleanup complete!"
echo ""
echo "📝 Note: Database migration files remain for backward compatibility."
echo "    If you want to remove them, delete:"
echo "    - src/main/resources/db/migration/V2__extend_figma_components_for_figma_import.sql (if it covers AI/Similarity tables)"
echo "    - src/main/resources/db/migration/V3__add_similarity_analysis_tables.sql (if exists)"
