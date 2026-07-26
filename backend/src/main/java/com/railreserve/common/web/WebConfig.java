package com.railreserve.common.web;

import com.railreserve.scheduling.domain.TravelClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the converter that lets {@code ?travelClass=SL} bind straight to the
 * {@link TravelClass} enum (via its external code) on controller parameters.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToTravelClassConverter());
    }

    static final class StringToTravelClassConverter implements Converter<String, TravelClass> {
        @Override
        public TravelClass convert(String source) {
            return TravelClass.fromCode(source);
        }
    }
}
