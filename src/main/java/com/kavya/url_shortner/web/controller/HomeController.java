package com.kavya.url_shortner.web.controller;

import com.kavya.url_shortner.ApplicationProperties;
import com.kavya.url_shortner.domain.Exceptions.ShortUrlNotFoundException;
import com.kavya.url_shortner.domain.model.CreateShortUrlCmd;
import com.kavya.url_shortner.domain.model.PagedResult;
import com.kavya.url_shortner.domain.model.shortUrlDto;
import com.kavya.url_shortner.domain.services.ShortUrlService;
import com.kavya.url_shortner.web.dtos.CreateShortUrlForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class HomeController {

    private final ApplicationProperties properties;
    private final ShortUrlService ShortUrlService;

    public HomeController(
            ApplicationProperties properties,
            ShortUrlService ShortUrlService
    ) {
        this.properties = properties;
        this.ShortUrlService = ShortUrlService;
    }

    private void addShortUrlsDataToModel(Model model, int pageNumber) {
        PagedResult<shortUrlDto> shortUrls =
                ShortUrlService.findAllPublicShortUrls(
                        pageNumber,
                        properties.pageSize()
                );

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("baseUrl", properties.baseUrl());
    }

    @GetMapping("/")
    public String home(
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        addShortUrlsDataToModel(model, page);
        model.addAttribute("paginationUrl", "/");
        model.addAttribute(
                "createShortUrlForm",
                new CreateShortUrlForm("")
        );

        return "index";
    }

    @PostMapping("/short-urls")
    public String createShortUrl(
            @ModelAttribute("createShortUrlForm")
            @Valid CreateShortUrlForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            addShortUrlsDataToModel(model, 1);
            return "index";
        }

        try {
            CreateShortUrlCmd command =
                    new CreateShortUrlCmd(form.originalUrl());

            shortUrlDto createdUrl =
                    ShortUrlService.createShortUrl(command);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Short URL created successfully: "
                            + properties.baseUrl()
                            + "/s/"
                            + createdUrl.shortKey()
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Failed to create short URL: "
                            + exception.getMessage()
            );
        }

        return "redirect:/";
    }

    @GetMapping("/s/{shortKey}")
    public String redirectToOriginalUrl(
            @PathVariable String shortKey
    ) {
        Optional<shortUrlDto> shortUrl =
                ShortUrlService.accessShortUrl(shortKey);

        if (shortUrl.isEmpty()) {
            throw new ShortUrlNotFoundException(
                    "Invalid or expired short key: " + shortKey
            );
        }

        return "redirect:" + shortUrl.get().originalUrl();
    }
}