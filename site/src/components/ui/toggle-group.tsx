import * as React from 'react';
import * as ToggleGroupPrimitive from '@radix-ui/react-toggle-group';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

// The app's mode selector: a surface pill holding rounded choices, the selected one raised.
const toggleVariants = cva(
  'inline-flex min-h-12 flex-1 items-center justify-center gap-2 rounded-[24px] px-5 text-[15px] font-medium whitespace-nowrap text-muted-foreground transition-colors duration-200 ease-out outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset data-[state=on]:bg-secondary data-[state=on]:text-foreground motion-reduce:transition-none [&_svg]:size-[18px] [&_svg]:shrink-0',
  { variants: { size: { default: '' } }, defaultVariants: { size: 'default' } },
);

function ToggleGroup({ className, children, ...props }: React.ComponentProps<typeof ToggleGroupPrimitive.Root>) {
  return (
    <ToggleGroupPrimitive.Root
      data-slot="toggle-group"
      className={cn('flex rounded-[28px] bg-card p-1', className)}
      {...props}
    >
      {children}
    </ToggleGroupPrimitive.Root>
  );
}

function ToggleGroupItem({
  className,
  size,
  ...props
}: React.ComponentProps<typeof ToggleGroupPrimitive.Item> & VariantProps<typeof toggleVariants>) {
  return <ToggleGroupPrimitive.Item data-slot="toggle-group-item" className={cn(toggleVariants({ size }), className)} {...props} />;
}

export { ToggleGroup, ToggleGroupItem };
