package com.figma.design.repository;

import com.figma.design.model.WebComponent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebComponentRepository extends JpaRepository<WebComponent, Long> {

	List<WebComponent> findByPage_Id(Long pageId);
}