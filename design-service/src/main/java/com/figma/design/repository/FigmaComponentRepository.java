package com.figma.design.repository;

import com.figma.design.model.FigmaComponent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FigmaComponentRepository extends JpaRepository<FigmaComponent, Long> {

	void deleteByPage_Id(Long pageId);

	List<FigmaComponent> findByPage_Id(Long pageId);

	Optional<FigmaComponent> findByFigmaNodeId(String figmaNodeId);
}