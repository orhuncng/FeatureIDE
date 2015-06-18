package de.ovgu.featureide.featurehouse.refactoring;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;


@SuppressWarnings("restriction")
public class RenameRefactoringWizardPage extends UserInputWizardPage {
	
	private static final String PAGE_NAME = "RefactoringInputPage";	
	private final RenameMethodRefactoring refactoring;
	private Text newNameField;
	
	public RenameRefactoringWizardPage(RenameRefactoringWizard wizard) {
		super(PAGE_NAME);
		refactoring = (RenameMethodRefactoring) wizard.getRefactoring();
	}
	
	@Override
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(2, false));
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        composite.setFont(parent.getFont());

        Label label= new Label(composite, SWT.NONE);
        label.setText(RefactoringUIMessages.RenameResourceWizard_name_field_label);
        label.setLayoutData(new GridData());

        newNameField= new Text(composite, SWT.BORDER);
        newNameField.setText(refactoring.getNewName());
        newNameField.setFont(composite.getFont());
        newNameField.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false));
        newNameField.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent e) {
                        validatePage();
                }
        });

        newNameField.setFocus();
        newNameField.selectAll();
        setPageComplete(false);
        setControl(composite);
	}
	
	protected final void validatePage() {
		refactoring.setNewName(newNameField.getText());
		RefactoringStatus status;
		try {
			status = refactoring.checkInitialConditions(new NullProgressMonitor());
			setPageComplete(status);
		} catch (OperationCanceledException | CoreException e) {
			e.printStackTrace();
		}
	}
	
}