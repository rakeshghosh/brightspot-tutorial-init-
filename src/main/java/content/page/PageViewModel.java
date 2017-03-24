package content.page;

import com.psddev.cms.view.ViewModel;
import styleguide.content.page.PageFooterView;
import styleguide.content.page.PageHeaderView;
import styleguide.content.page.PageView;
import styleguide.content.page.PageViewFooterField;
import styleguide.content.page.PageViewHeaderField;
import styleguide.content.page.PageViewMainField;

import java.util.Collections;

public class PageViewModel extends ViewModel<Page> implements PageView {

    @Override
    public String getTitle() {
        return "Brightspot Tutorial";
    }

    @Override
    public Iterable<? extends PageViewHeaderField> getHeader() {
        return Collections.singletonList(new PageHeaderView.Builder().build());
    }

    @Override
    public Iterable<? extends PageViewMainField> getMain() {
        return Collections.singletonList(createView(PageViewMainField.class, model));
    }

    @Override
    public Iterable<? extends PageViewFooterField> getFooter() {
        return Collections.singletonList(new PageFooterView.Builder().build());
    }
}
