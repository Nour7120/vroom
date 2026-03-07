package com.county_cars.vroom.modules.attachment.mapper;

import com.county_cars.vroom.modules.attachment.dto.response.AttachmentResponse;
import com.county_cars.vroom.modules.attachment.entity.Attachment;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttachmentMapper {

    AttachmentResponse toResponse(Attachment entity);
}
