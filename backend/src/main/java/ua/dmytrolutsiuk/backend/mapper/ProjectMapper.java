package ua.dmytrolutsiuk.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.payload.response.ProjectResponse;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    List<ProjectResponse> toResponses(List<Project> projects);
}
